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
 * Strategy validation rule component that prevents a passenger from booking
 * multiple different routes on the same calendar date.
 *
 * <p>This class implements {@link BookingRule} to enforce a "one booking per
 * date" policy across routes. A passenger may still book multiple routes, but
 * each must fall on a distinct departure date.</p>
 */
@Component
public class DateConflictBookingRule implements BookingRule {

    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String errorMessage = "";

    public DateConflictBookingRule(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    @Override
    public boolean validate(User user, Trip trip) {
        if (!(user instanceof Passenger)) {
            errorMessage = "Only registered passengers can make shuttle reservations.";
            return false;
        }

        if (trip == null || trip.getSchedule() == null) {
            // No schedule to compare against; allow other rules to decide.
            return true;
        }

        LocalDate newDate = trip.getSchedule().getDepartureDate();
        if (newDate == null) {
            return true;
        }

        try {
            Long passengerId = user.getUserId();
            String json = supabaseService.getActiveReservationsWithTripDetails(passengerId);
            JsonNode array = objectMapper.readTree(json);

            // Count how many active reservations the passenger already has on this date.
            int bookingsOnDate = 0;
            if (array.isArray()) {
                for (JsonNode existing : array) {
                    JsonNode existingTrip = existing.path("trip");
                    JsonNode existingSchedule = existingTrip.path("schedule");
                    String existingDateStr = existingSchedule.path("departure_date").asText(null);

                    if (existingDateStr != null && existingDateStr.equals(newDate.toString())) {
                        bookingsOnDate++;
                    }
                }
            }

            // Allow up to 2 bookings per day (different routes permitted).
            // Block only if the passenger already has 2+ bookings on this date.
            if (bookingsOnDate >= 2) {
                errorMessage = "You already have 2 active bookings on " + newDate
                        + ". Please choose a different date.";
                return false;
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = "Unable to verify date conflicts against Supabase.";
            return false;
        }
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
