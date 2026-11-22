package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * JavaFX controller for the Administrator Dashboard view.
 * <p>
 * This class manages the main dashboard interface for administrative users.
 * It is responsible for asynchronously fetching and aggregating system statistics
 * (such as total users, active trips, available seats, and reservations) via
 * {@link SupabaseService}, and safely updating the UI components on the JavaFX
 * Application Thread. Additionally, it handles UI navigation to other administrative
 * modules like Route, Schedule, and Trip management using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class AdminDashboardController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper;

    @FXML private Label totalUsers;
    @FXML private Label todaysTrips;
    @FXML private Label availableSeats;
    @FXML private Label activeTrips;
    @FXML private Label reservations;

    public AdminDashboardController(SupabaseService supabaseService, UserSession userSession, ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.objectMapper = new ObjectMapper();
    }

    @FXML
    public void initialize() {
        loadDashboardStatisticsAsync();
    }

    private void loadDashboardStatisticsAsync() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // 1. Get Total Users Count
                String usersJson = supabaseService.getAllUsers();
                int usersCount = objectMapper.readTree(usersJson).size();

                // 2. Get Reservations Count
                String reservationsJson = supabaseService.getReservations();
                int resCount = objectMapper.readTree(reservationsJson).size();

                // 3. Get Trips and Departure Schedules
                String tripsJson = supabaseService.getTrips();
                JsonNode tripsArray = objectMapper.readTree(tripsJson);

                String schedulesJson = supabaseService.getSchedules();
                JsonNode schedulesArray = objectMapper.readTree(schedulesJson);

// Map schedule IDs to their departure dates
                Map<Long, LocalDate> scheduleDates = new HashMap<>();

                for (JsonNode schedule : schedulesArray) {
                    long scheduleId = schedule.path("id").asLong();

                    String departureDate = schedule.path("departure_date").asText("");

                    if (!departureDate.isEmpty()) {
                        scheduleDates.put(scheduleId, LocalDate.parse(departureDate));
                    }
                }

// Initialize statistics
                int todayCount = 0;
                int activeCount = 0;
                int availableSeatsCount = 0;

// Get today's date in Philippine time
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));

// Process Trips
                for (JsonNode trip : tripsArray) {

                    String status = trip.path("status").asText("");

                    // Get the departure date through schedule_id
                    long scheduleId = trip.path("schedule_id").asLong();

                    LocalDate departureDate = scheduleDates.get(scheduleId);

                    // Count Today's Trips
                    if (departureDate != null && departureDate.equals(today)) {
                        todayCount++;
                    }

                    // Count Active Trips
                    if ("IN_TRANSIT".equals(status) || "BOARDING".equals(status)) {
                        activeCount++;
                    }

                    // Calculate Available Seats
                    if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status)) {

                        int capacity = trip.path("capacity").asInt(0);
                        int seatsTaken = trip.path("seats_taken").asInt(0);

                        availableSeatsCount += Math.max(0, capacity - seatsTaken);
                    }
                }

                final String fUsers = String.valueOf(usersCount);
                final String fToday = String.valueOf(todayCount);
                final String fSeats = String.valueOf(availableSeatsCount);
                final String fActive = String.valueOf(activeCount);
                final String fRes = String.valueOf(resCount);

                // Update UI safely on JavaFX thread
                Platform.runLater(() -> {
                    totalUsers.setText(fUsers);
                    todaysTrips.setText(fToday);
                    availableSeats.setText(fSeats);
                    activeTrips.setText(fActive);
                    reservations.setText(fRes);
                });

                return null;
            }
        };

        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            Platform.runLater(() -> {
                totalUsers.setText("Error");
                todaysTrips.setText("Error");
                availableSeats.setText("Error");
                activeTrips.setText("Error");
                reservations.setText("Error");
            });
        });

        new Thread(task).start();
    }

    // --- Navigation Handlers ---

    @FXML
    private void handleDashboard(ActionEvent event) {
        // Already here
    }

    @FXML
    private void handleRouteManagement(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/RouteManagement.fxml", 1100, 700);
    }

    @FXML
    private void handleScheduleManagement(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/ScheduleManagement.fxml", 1100, 700);
    }

    @FXML
    private void handleTripManagement(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/TripManagement.fxml", 1100, 700);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }
}