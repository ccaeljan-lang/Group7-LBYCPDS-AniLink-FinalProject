package ph.edu.dlsu.anilink.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.model.WaitingList;
import ph.edu.dlsu.anilink.service.ReservationService;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.service.WaitingListService;

import java.util.List;

/**
 * REST controller for managing passenger reservations.
 * <p>
 * This class exposes API endpoints under {@code /api/reservations} to handle client
 * requests related to {@link Reservation} data. It provides functionality to retrieve all
 * reservations, fetch a specific reservation by its ID, create new reservations, and cancel
 * existing ones. It interacts with {@link ReservationService} for business logic and
 * {@link SupabaseService} for data retrieval and updates.
 * </p>
 */
@RestController("apiReservationController")
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final SupabaseService supabaseService;
    private final WaitingListService waitingListService;
    private final ObjectMapper objectMapper;

    public ReservationController(
            ReservationService reservationService,
            SupabaseService supabaseService,
            WaitingListService waitingListService) {
        this.reservationService = reservationService;
        this.supabaseService = supabaseService;
        this.waitingListService = waitingListService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations() {
        try {
            String json = supabaseService.getAllReservations();
            List<Reservation> list = objectMapper.readValue(json, new TypeReference<List<Reservation>>() {});
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservationById(@PathVariable Long id) {
        try {
            String json = supabaseService.getReservationById(id);
            List<Reservation> list = objectMapper.readValue(json, new TypeReference<List<Reservation>>() {});
            if (list.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(list.get(0));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody ReservationRequest request) {
        try {
            Reservation reservation = reservationService.createReservation(
                    request.passenger(),
                    request.trip()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create reservation.");
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable Long id) {
        try {
            String json = supabaseService.getReservationById(id);
            List<Reservation> list = objectMapper.readValue(json, new TypeReference<List<Reservation>>() {});
            if (list.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Reservation reservation = list.get(0);
            supabaseService.updateReservationStatus(id, Reservation.CANCELLED);

            Long tripId = null;
            if (reservation.getTrip() != null) {
                tripId = reservation.getTrip().getTripId();
                int updatedSeats = Math.max(0, reservation.getTrip().getSeatsTaken() - 1);
                supabaseService.updateTripSeatsTaken(tripId, updatedSeats);
            }

            // Promote the next waiting passenger now that a seat has freed up
            if (tripId != null) {
                WaitingList promoted = waitingListService.promoteNext(tripId);
                if (promoted != null && promoted.getPassengerId() != null) {
                    try {
                        String userJson = supabaseService.getUserById(promoted.getPassengerId());
                        JsonNode userNode = objectMapper.readTree(userJson);
                        if (userNode.isArray() && !userNode.isEmpty()) {
                            JsonNode u = userNode.get(0);
                            String role = u.path("role").asText("PASSENGER");
                            User passenger;
                            switch (role.toUpperCase()) {
                                case "DRIVER":
                                    passenger = new ph.edu.dlsu.anilink.model.Driver(
                                            u.path("id").asLong(),
                                            u.path("name").asText(),
                                            u.path("email").asText(),
                                            u.path("password").asText(),
                                            u.path("license_number").asText(null)
                                    );
                                    break;
                                case "ADMINISTRATOR":
                                case "ADMIN":
                                    passenger = new ph.edu.dlsu.anilink.model.Administrator(
                                            u.path("id").asLong(),
                                            u.path("name").asText(),
                                            u.path("email").asText(),
                                            u.path("password").asText(),
                                            u.path("department").asText(null)
                                    );
                                    break;
                                default:
                                    passenger = new ph.edu.dlsu.anilink.model.Passenger(
                                            u.path("id").asLong(),
                                            u.path("name").asText(),
                                            u.path("email").asText(),
                                            u.path("password").asText(),
                                            u.path("category").asText(null)
                                    );
                                    break;
                            }

                            Trip trip = new Trip();
                            trip.setTripId(tripId);
                            trip.setCapacity(reservation.getTrip().getCapacity());
                            trip.setSeatsTaken(reservation.getTrip().getSeatsTaken());
                            trip.setStatus(reservation.getTrip().getStatus());

                            reservationService.createReservation(passenger, trip);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Promotion failure should not roll back the cancellation
                    }
                }
            }

            reservation.cancel();
            return ResponseEntity.ok(reservation);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to cancel reservation.");
        }
    }

    public record ReservationRequest(
            User passenger,
            Trip trip
    ) {
    }
}