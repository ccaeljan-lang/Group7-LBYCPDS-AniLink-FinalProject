package ph.edu.dlsu.anilink.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.TripFullException;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.model.WaitingList;

import java.util.List;
import java.util.UUID;

/**
 * Service managing reservation lifecycles, validation, and Supabase synchronization.
 *
 * <p>Key components:
 * <ul>
 *   <li><b>Validation:</b> Delegates pre-booking rules to {@link BookingValidationService}.</li>
 *   <li><b>QR Generation:</b> Assigns unique UUID payloads for boarding verification.</li>
 *   <li><b>Persistence:</b> Atomically updates trip seat counts prior to persisting reservations to prevent overbooking.</li>
 * </ul>
 * </p>
 *
 * @see ph.edu.dlsu.anilink.service.SupabaseService
 * @see ph.edu.dlsu.anilink.service.BookingValidationService
 */
@Service
public class ReservationService {

    private final SupabaseService supabaseService;
    private final BookingValidationService bookingValidationService;
    private final WaitingListService waitingListService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ReservationService(
            SupabaseService supabaseService,
            BookingValidationService bookingValidationService,
            WaitingListService waitingListService) {
        this.supabaseService = supabaseService;
        this.bookingValidationService = bookingValidationService;
        this.waitingListService = waitingListService;
    }

    public boolean canBook(User user, Trip trip) {
        if (user == null || trip == null || trip.isFull()) {
            return false;
        }
        return bookingValidationService.validateBooking(user, trip).isValid();
    }

    public String getBookingError(User user, Trip trip) {
        if (user == null || trip == null) {
            return "Invalid passenger or trip selected.";
        }
        if (trip.isFull()) {
            return "This trip is already fully booked.";
        }

        BookingValidationService.ValidationResult result =
                bookingValidationService.validateBooking(user, trip);

        return result.isValid() ? "" : result.getMessage();
    }

    public Reservation createReservation(User user, Trip trip) {
        if (!canBook(user, trip)) {
            if (trip != null && trip.isFull()) {
                throw new TripFullException("This trip is fully booked. You can join the waiting list.");
            }
            throw new IllegalStateException(getBookingError(user, trip));
        }

        String qrPayload = "ANILINK-RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // 1. Post the reservation record FIRST so a seat is never counted
            //    without an actual reservation row. postReservation writes
            //    status = CONFIRMED for an allocated seat reservation.
            String responseJson = supabaseService.postReservation(
                    user.getUserId(),
                    trip.getTripId(),
                    qrPayload
            );

            // 2. Parse created reservation response
            List<Reservation> createdList = objectMapper.readValue(
                    responseJson,
                    new TypeReference<List<Reservation>>() {}
            );

            if (createdList == null || createdList.isEmpty()) {
                throw new RuntimeException("Failed to retrieve created reservation record from Supabase.");
            }

            Reservation created = createdList.get(0);

            // 3. Count the seat only after the reservation row exists. If the seat
            //    update fails, the reservation is still valid and syncSeatsTaken()
            //    can reconcile the count; the reverse (seat taken, no reservation)
            //    is the unsafe state we avoid.
            int updatedSeats = trip.getSeatsTaken() + 1;
            supabaseService.updateTripSeatsTaken(trip.getTripId(), updatedSeats);
            trip.setSeatsTaken(updatedSeats);

            // 4. Surface the persisted status (CONFIRMED) and unique QR payload.
            created.setStatus(Reservation.CONFIRMED);
            return created;

        } catch (TripFullException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to save reservation to Supabase: " + e.getMessage(), e);
        }
    }

    /**
     * Adds the passenger to the waiting list for a trip when the trip is full.
     *
     * @return true if the passenger joined the waiting list, false if already on it
     */
    public boolean joinWaitingList(User user, Trip trip) {
        if (user == null || trip == null) {
            return false;
        }
        return waitingListService.joinWaitlist(
                trip.getTripId(),
                user.getUserId(),
                user.getName()
        );
    }

    /**
     * Returns the waiting list position (1-based) for a passenger on a trip,
     * or -1 if not on the list.
     */
    public int getWaitingListPosition(User user, Trip trip) {
        if (user == null || trip == null) {
            return -1;
        }
        return waitingListService.getPosition(trip.getTripId(), user.getUserId());
    }

    /**
     * Removes the passenger from the waiting list for a trip.
     *
     * @return true if the passenger was removed, false if not on the list
     */
    public boolean cancelWaitlist(User user, Trip trip) {
        if (user == null || trip == null) {
            return false;
        }
        return waitingListService.cancelWaitlist(trip.getTripId(), user.getUserId());
    }

    /**
     * Repeats a previous reservation by creating a new booking on the same trip
     * (same route and departure schedule).
     *
     * <p>Seat availability is checked before creating the new reservation to prevent
     * overbooking or duplicate active reservations on a full trip.</p>
     *
     * @param user                the passenger repeating the booking
     * @param originalReservationId the ID of the existing reservation to repeat
     * @return the newly created {@link Reservation}
     * @throws TripFullException if the trip is fully booked
     * @throws IllegalStateException if the original reservation or trip cannot be found
     */
    public Reservation repeatReservation(User user, Long originalReservationId) {
        if (user == null || originalReservationId == null) {
            throw new IllegalArgumentException("User and reservation ID are required.");
        }

        try {
            // 1. Fetch original reservation to get the trip ID
            String json = supabaseService.getReservationById(originalReservationId);
            List<Reservation> reservations = objectMapper.readValue(
                    json,
                    new TypeReference<List<Reservation>>() {}
            );

            if (reservations == null || reservations.isEmpty()) {
                throw new IllegalStateException("Original reservation not found.");
            }

            Long tripId = reservations.get(0).getTrip().getTripId();

            // 2. Fetch full trip details (includes route and schedule) for availability check
            String tripJson = supabaseService.getTripDetails(tripId);
            List<Trip> tripList = objectMapper.readValue(
                    tripJson,
                    new TypeReference<List<Trip>>() {}
            );

            if (tripList == null || tripList.isEmpty()) {
                throw new IllegalStateException("Unable to retrieve trip details for repeat booking.");
            }

            Trip originalTrip = tripList.get(0);

            // 3. Construct a Trip for the repeat booking with same route and schedule
            Trip repeatTrip = new Trip();
            repeatTrip.setTripId(originalTrip.getTripId());
            repeatTrip.setRoute(originalTrip.getRoute());
            repeatTrip.setSchedule(originalTrip.getSchedule());
            repeatTrip.setCapacity(originalTrip.getCapacity());
            repeatTrip.setSeatsTaken(originalTrip.getSeatsTaken());
            repeatTrip.setStatus(originalTrip.getStatus());

            // 4. Check seat availability before booking
            if (!canBook(user, repeatTrip)) {
                if (repeatTrip.isFull()) {
                    throw new TripFullException("This trip is fully booked. You can join the waiting list.");
                }
                throw new IllegalStateException(getBookingError(user, repeatTrip));
            }

            // 5. Create the new reservation
            return createReservation(user, repeatTrip);

        } catch (TripFullException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to repeat reservation: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels a reservation and promotes the next waiting passenger into the
     * freed seat. This is the single cancellation/promotion operation used by
     * both the JavaFX UI and the REST API (see {@code backend.ReservationController}).
     *
     * <p>Steps:
     * <ol>
     *   <li>Reject null or missing reservation IDs.</li>
     *   <li>Reject CANCELLED or COMPLETED bookings: no double seat decrement and
     *       no double promotion.</li>
     *   <li>Mark the reservation CANCELLED in Supabase.</li>
     *   <li>Decrement the trip's seats_taken using fresh trip details.</li>
     *   <li>If the trip is still active, promote the next FIFO waiting passenger
     *       into the freed seat: create an actual CONFIRMED reservation for them,
     *       and only then mark their waitlist entry CONFIRMED.</li>
     * </ol>
     * Promotion is intentionally not rolled back if it fails: the cancellation
     * stands, the freed seat stays available, and a retry can promote again.
     * </p>
     *
     * @param reservationId the ID of the reservation to cancel
     * @return the cancelled {@link Reservation}
     * @throws IllegalStateException if the reservation cannot be found or is already cancelled/completed
     */
    public Reservation cancelReservationAndPromote(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Reservation ID is required.");
        }

        try {
            // 1. Fetch the reservation to get trip info and current status
            String json = supabaseService.getReservationById(reservationId);
            List<Reservation> reservations = objectMapper.readValue(
                    json,
                    new TypeReference<List<Reservation>>() {}
            );

            if (reservations == null || reservations.isEmpty()) {
                throw new IllegalStateException("Reservation not found.");
            }

            Reservation existing = reservations.get(0);
            String status = existing.getStatus() != null ? existing.getStatus() : "";

            if (Reservation.CANCELLED.equalsIgnoreCase(status)) {
                throw new IllegalStateException("This reservation is already cancelled.");
            }
            if (Reservation.COMPLETED.equalsIgnoreCase(status)) {
                throw new IllegalStateException("This reservation is completed and cannot be cancelled.");
            }

            Long tripId = existing.getTrip() != null ? existing.getTrip().getTripId() : null;

            // 2. Mark reservation as CANCELLED in Supabase
            supabaseService.updateReservationStatus(reservationId, Reservation.CANCELLED);

            // 3. Decrement seats_taken using fresh trip details (never a stale copy)
            int updatedSeats = 0;
            String tripStatus = Trip.SCHEDULED;
            if (tripId != null) {
                try {
                    String tripJson = supabaseService.getTripDetails(tripId);
                    List<Trip> tripList = objectMapper.readValue(
                            tripJson,
                            new TypeReference<List<Trip>>() {}
                    );
                    if (tripList != null && !tripList.isEmpty()) {
                        Trip fresh = tripList.get(0);
                        int currentSeatsTaken = fresh.getSeatsTaken();
                        updatedSeats = Math.max(0, currentSeatsTaken - 1);
                        tripStatus = fresh.getStatus() != null ? fresh.getStatus() : Trip.SCHEDULED;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // Fall back to the reservation's cached trip state
                    int currentSeatsTaken = existing.getTrip() != null
                            ? existing.getTrip().getSeatsTaken() : 0;
                    updatedSeats = Math.max(0, currentSeatsTaken - 1);
                }
                supabaseService.updateTripSeatsTaken(tripId, updatedSeats);
            }

            // 4. Promote the next waiting passenger into the freed seat, but only
            //    into an active trip. Never promote into a cancelled/completed trip.
            if (tripId != null && isActiveTrip(tripStatus)) {
                try {
                    promoteAndReserve(tripId);
                } catch (Exception ex) {
                    // Promotion failure must not roll back the cancellation. The freed
                    // seat stays available and the next cancellation/retry can promote.
                    ex.printStackTrace();
                }
            }

            existing.cancel();
            return existing;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to cancel reservation: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels a reservation from the passenger's standpoint.
     *
     * <p>Delegates to {@link #cancelReservationAndPromote(Long)} so the UI and the
     * REST API share exactly one validated cancellation/promotion workflow and
     * cannot diverge in behavior.</p>
     *
     * @param reservationId the ID of the reservation to cancel
     * @return the cancelled {@link Reservation}
     * @throws IllegalStateException if the reservation cannot be found or is already cancelled/completed
     * @see #cancelReservationAndPromote(Long)
     */
    public Reservation cancelReservation(Long reservationId) {
        return cancelReservationAndPromote(reservationId);
    }

    /**
     * Promotes the next FIFO waiting passenger for a trip into an actual
     * CONFIRMED reservation.
     *
     * <p>The waitlist entry is marked CONFIRMED only after the reservation row is
     * successfully persisted. This is NOT a multi-call atomic transaction (Supabase
     * is reached through independent REST calls). On any failure the entry is left
     * WAITING so it can be retried; no seat is falsely taken and no
     * CONFIRMED-without-reservation state is created.</p>
     *
     * @param tripId the trip whose freed seat is being filled
     */
    private void promoteAndReserve(Long tripId) {
        WaitingList candidate = waitingListService.promoteNext(tripId);
        if (candidate == null || candidate.getPassengerId() == null) {
            return; // no one waiting
        }

        Long passengerId = candidate.getPassengerId();

        // Validate the passenger is a real PASSENGER before promoting.
        User passenger = fetchUserAsPassenger(passengerId);
        if (passenger == null) {
            // Not a passenger / not found: leave the entry WAITING, non-destructively.
            return;
        }

        // Always retrieve fresh, complete trip details (route + schedule) before
        // validating/promoting. Never promote with a half-populated Trip.
        Trip freshTrip = fetchFreshTrip(tripId);
        if (freshTrip == null) {
            return;
        }

        Trip bookingTrip = new Trip();
        bookingTrip.setTripId(freshTrip.getTripId());
        bookingTrip.setRoute(freshTrip.getRoute());
        bookingTrip.setSchedule(freshTrip.getSchedule());
        bookingTrip.setCapacity(freshTrip.getCapacity());
        bookingTrip.setSeatsTaken(freshTrip.getSeatsTaken());
        bookingTrip.setStatus(freshTrip.getStatus());

        // Validate passenger role, route/booking quota, duplicate reservations,
        // seat availability and total capacity via the normal booking path.
        if (!canBook(passenger, bookingTrip)) {
            // Ineligible (quota/duplicate/full): leave entry WAITING, non-destructively.
            return;
        }

        // Create the actual CONFIRMED reservation with a unique UUID-based QR payload.
        createReservation(passenger, bookingTrip);

        // Only now, after the reservation really exists, mark the waitlist entry CONFIRMED.
        // The conditional update (WAITING only) guards against double promotion on retry.
        waitingListService.confirmPromotion(candidate.getId());
    }

    private User fetchUserAsPassenger(Long passengerId) {
        try {
            String userJson = supabaseService.getUserById(passengerId);
            com.fasterxml.jackson.databind.JsonNode userNode = objectMapper.readTree(userJson);
            if (userNode == null || !userNode.isArray() || userNode.isEmpty()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode u = userNode.get(0);
            String role = u.path("role").asText("PASSENGER");
            if (!"PASSENGER".equalsIgnoreCase(role)) {
                return null;
            }
            return new ph.edu.dlsu.anilink.model.Passenger(
                    u.path("id").asLong(),
                    u.path("name").asText(),
                    u.path("email").asText(),
                    u.path("password").asText(),
                    u.path("category").asText(null)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Trip fetchFreshTrip(Long tripId) {
        try {
            String tripJson = supabaseService.getTripDetails(tripId);
            List<Trip> tripList = objectMapper.readValue(
                    tripJson,
                    new TypeReference<List<Trip>>() {}
            );
            if (tripList == null || tripList.isEmpty()) {
                return null;
            }
            return tripList.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isActiveTrip(String status) {
        if (status == null) {
            return false;
        }
        return !Trip.COMPLETED.equalsIgnoreCase(status)
                && !Trip.CANCELLED.equalsIgnoreCase(status);
    }
}