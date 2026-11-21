package ph.edu.dlsu.anilink.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ph.edu.dlsu.anilink.model.Administrator;
import ph.edu.dlsu.anilink.model.Driver;
import ph.edu.dlsu.anilink.model.Passenger;
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
            // Single validated cancellation/promotion workflow shared with the JavaFX UI.
            // Never build a stale/incomplete Trip here; the service fetches fresh details.
            Reservation cancelled = reservationService.cancelReservationAndPromote(id);
            return ResponseEntity.ok(cancelled);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to cancel reservation.");
        }
    }

    public record ReservationRequest(
            User passenger,
            Trip trip
    ) {
    }
}