package ph.edu.dlsu.anilink.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.TripFullException;
import ph.edu.dlsu.anilink.model.User;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // 1. Reserve seat in Supabase first (if this fails, no reservation record is created)
            int updatedSeats = trip.getSeatsTaken() + 1;
            supabaseService.updateTripSeatsTaken(trip.getTripId(), updatedSeats);

            // 2. Post reservation record to Supabase
            String responseJson = supabaseService.postReservation(
                    user.getUserId(),
                    trip.getTripId(),
                    qrPayload
            );

            // 3. Parse created reservation response
            List<Reservation> createdList = objectMapper.readValue(
                    responseJson,
                    new TypeReference<List<Reservation>>() {}
            );

            if (createdList == null || createdList.isEmpty()) {
                throw new RuntimeException("Failed to retrieve created reservation record from Supabase.");
            }

            // 4. Update local trip object state only after both remote calls succeed
            trip.setSeatsTaken(updatedSeats);

            return createdList.get(0);

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
}