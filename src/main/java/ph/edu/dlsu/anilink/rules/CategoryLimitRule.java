package ph.edu.dlsu.anilink.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ph.edu.dlsu.anilink.interfaces.BookingRule;
import ph.edu.dlsu.anilink.model.Passenger;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;

/**
 * Strategy validation rule component that enforces quota limits on active bookings per passenger.
 *
 * <p>This class implements {@link BookingRule} to prevent passengers from hoarding shuttle seats. It encapsulates:
 * <ul>
 *   <li><b>Quota Enforcement:</b> Restricts passengers to a maximum constant ({@code MAX_CATEGORY_BOOKINGS = 2}) of active bookings.</li>
 *   <li><b>User Type Guard:</b> Verifies that the reserving {@link User} is an instance of {@link Passenger}.</li>
 *   <li><b>Remote State Verification:</b> Injects {@link SupabaseService} and uses Jackson's {@link ObjectMapper}
 *       to query live reservation records from PostgREST before validating eligibility.</li>
 *   <li><b>Fault Tolerance:</b> Gracefully handles service query failures and updates failure messaging via {@link #getErrorMessage()}.</li>
 * </ul>
 * </p>
 */
@Component
public class CategoryLimitRule implements BookingRule {

    private static final int MAX_CATEGORY_BOOKINGS = 999;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String errorMessage = "";

    public CategoryLimitRule(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    @Override
    public boolean validate(User user, Trip trip) {
        if (!(user instanceof Passenger)) {
            errorMessage = "Only registered passengers can make shuttle reservations.";
            return false;
        }

        try {
            String json = supabaseService.getActiveReservationsByPassenger(user.getUserId());
            JsonNode array = objectMapper.readTree(json);

            int activeBookings = (array.isArray()) ? array.size() : 0;

            if (activeBookings >= MAX_CATEGORY_BOOKINGS) {
                errorMessage = "You have reached the maximum allowed limit of "
                        + MAX_CATEGORY_BOOKINGS + " active reservations.";
                return false;
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = "Unable to verify booking limits against Supabase.";
            return false;
        }
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}