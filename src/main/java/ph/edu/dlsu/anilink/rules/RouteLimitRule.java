package ph.edu.dlsu.anilink.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ph.edu.dlsu.anilink.interfaces.BookingRule;
import ph.edu.dlsu.anilink.model.Passenger;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;

import java.time.LocalDate;

/**
 * Strategy validation rule component that enforces per-route active reservation limits
 * for passengers, checked per departure date.
 *
 * <p>This class implements {@link BookingRule} to prevent duplicate active bookings
 * on the same route for the same departure date. A passenger may book the same route
 * on different dates (as long as the total active reservation count via
 * {@link CategoryLimitRule} is respected).</p>
 */
@Component
public class RouteLimitRule implements BookingRule {

    private static final int MAX_ROUTE_BOOKINGS = 1;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String errorMessage = "";

    public RouteLimitRule(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    @Override
    public boolean validate(User user, Trip trip) {
        if (!(user instanceof Passenger)) {
            errorMessage = "Only registered passengers can make shuttle reservations.";
            return false;
        }

        if (trip == null || trip.getRoute() == null || trip.getRoute().getRouteId() == null) {
            errorMessage = "Invalid route selection for booking validation.";
            return false;
        }

        if (trip.getSchedule() == null || trip.getSchedule().getDepartureDate() == null) {
            errorMessage = "The selected trip has no departure date.";
            return false;
        }

        try {
            Long passengerId = user.getUserId();
            Long routeId = trip.getRoute().getRouteId();
            LocalDate newDate = trip.getSchedule().getDepartureDate();

            String json = supabaseService.getActiveReservationsByPassengerAndRoute(passengerId, routeId);
            JsonNode array = objectMapper.readTree(json);

            int routeBookings = 0;
            if (array.isArray()) {
                for (JsonNode existing : array) {
                    JsonNode existingTrip = existing.path("trip");
                    JsonNode existingSchedule = existingTrip.path("schedule");
                    String existingDateStr = existingSchedule.path("departure_date").asText(null);

                    if (existingDateStr != null && existingDateStr.equals(newDate.toString())) {
                        routeBookings++;
                    }
                }
            }

            if (routeBookings >= MAX_ROUTE_BOOKINGS) {
                errorMessage = "You already have an active reservation for this route on " + newDate + ".";
                return false;
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = "Unable to verify route booking limits against Supabase.";
            return false;
        }
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}