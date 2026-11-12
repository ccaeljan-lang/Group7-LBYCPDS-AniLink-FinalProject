package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.model.WaitingList;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX controller for the Passenger Dashboard view.
 * <p>
 * This class manages the main landing interface for passenger users. It retrieves
 * the currently logged-in user from the {@link UserSession} to display a personalized
 * welcome message and provides an overview of the user's trip status. Additionally,
 * it handles user interactions and navigation to other passenger-specific modules,
 * such as booking reservations, viewing reservation history, and logging out, using
 * the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class PassengerDashboardController {
    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label passengerNameLabel;
    @FXML private Label currentTrip;
    @FXML private Label currentStatus;
    @FXML private ListView<String> tripsListView;
    @FXML private Button bookTripButton;
    @FXML private Button logoutButton;

    public PassengerDashboardController(SupabaseService supabaseService, UserSession userSession, ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    @FXML
    private void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null) {
            if (passengerNameLabel != null) {
                passengerNameLabel.setText("Welcome, " + user.getName());
            }
            loadPassengerReservationsAsync(user.getUserId());
            checkWaitingListPromotion(user.getUserId());
        }
    }

    private void loadPassengerReservationsAsync(Long passengerId) {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> tripHistory = new ArrayList<>();

                // Fetches reservations for passenger with embedded trip, route, and schedule details
                String json = supabaseService.getReservationsByPassenger(passengerId);
                JsonNode reservations = objectMapper.readTree(json);

                boolean activeFound = false;

                if (reservations.isArray() && !reservations.isEmpty()) {
                    for (JsonNode res : reservations) {
                        String status = res.path("status").asText("WAITLISTED");
                        long reservationId = res.path("id").asLong();

                        JsonNode trip = res.path("trip");
                        JsonNode route = trip.path("route");
                        JsonNode schedule = trip.path("schedule");

                        String origin = route.path("origin").asText("Unknown");
                        String destination = route.path("destination").asText("Unknown");
                        String departureTime = schedule.path("departure_time").asText("N/A");

                        String routeText = origin + " ↔ " + destination;

                        // Display the first non-cancelled/non-completed booking as active at the top
                        if (!activeFound && ("WAITLISTED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status))) {
                            activeFound = true;
                            final String activeRoute = routeText + " (" + departureTime + ")";
                            final String activeStat = status.toUpperCase();

                            Platform.runLater(() -> {
                                if (currentTrip != null) currentTrip.setText(activeRoute);
                                if (currentStatus != null) currentStatus.setText(activeStat);
                            });
                        }

                        // Add to list view
                        tripHistory.add(String.format("Ticket #%d | %s | Time: %s | Status: %s",
                                reservationId, routeText, departureTime, status.toUpperCase()));
                    }
                }

                if (!activeFound) {
                    Platform.runLater(() -> {
                        if (currentTrip != null) currentTrip.setText("No Active Reservation");
                        if (currentStatus != null) currentStatus.setText("NONE");
                    });
                }

                if (tripHistory.isEmpty()) {
                    tripHistory.add("No reservation records found.");
                }

                return tripHistory;
            }
        };

        task.setOnSucceeded(e -> {
            if (tripsListView != null) {
                tripsListView.getItems().clear();
                tripsListView.getItems().addAll(task.getValue());
            }
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            showAlert("Database Error", "Failed to retrieve passenger reservations.");
        });

        new Thread(task).start();
    }

    private void checkWaitingListPromotion(Long passengerId) {
        Task<StringBuilder> task = new Task<>() {
            @Override
            protected StringBuilder call() throws Exception {
                StringBuilder newConfirmedTrips = new StringBuilder();

                // Fetches waiting list of passenger with embedded trip, route, and schedule details
                String json = supabaseService.getWaitingListByPassenger(passengerId);
                JsonNode waitlists = objectMapper.readTree(json);

                if (waitlists.isArray() && !waitlists.isEmpty()) {
                    for (JsonNode node : waitlists) {
                        long id = node.path("id").asLong();
                        String status = node.path("status").asText("N/A");
                        String joinedAt = node.path("joined_at").asText("N/A").split("T")[0];
                        boolean isNotified = node.path("notified").asBoolean();

                        JsonNode trip = node.path("trip");
                        JsonNode route = trip.path("route");
                        JsonNode schedule = trip.path("schedule");

                        String origin = route.path("origin").asText("Unknown");
                        String destination = route.path("destination").asText("Unknown");
                        String routeText = origin + " ↔ " + destination;
                        String departureTime = schedule.path("departure_time").asText("N/A");

                        // Gather newly confirmed trips from the passenger's waiting list
                        if (status.equalsIgnoreCase(WaitingList.STATUS_CONFIRMED) && !isNotified) {
                            newConfirmedTrips.append("WAIT-").append(id).append(" | ")
                                    .append(routeText).append(" | ")
                                    .append(joinedAt).append(" | ")
                                    .append(departureTime).append("\n");

                            // Ensures that notifications for confirmed trips aren't repeated in the future
                            supabaseService.updateWaitingListNotified(id, true);
                        }
                    }
                }

                return newConfirmedTrips;
            }
        };

        task.setOnSucceeded(e -> {
            if (!task.getValue().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Waiting List Promotion");
                alert.setHeaderText("The wait is over!\nYou are now confirmed in the following trips:");
                alert.setContentText(task.getValue().toString());
                alert.showAndWait();
            }
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            showAlert("Database Error", "Failed to retrieve passenger waiting list.");
        });

        new Thread(task).start();
    }

    // Navigation Handlers
    @FXML
    private void handleHome(ActionEvent event) {
        // Already on Home/Dashboard view
    }

    @FXML
    private void handleReservations(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/Reservation.fxml", 1000, 650);
    }

    @FXML
    private void handleBookSelectedTrip(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/MyReservations.fxml", 1000, 650);
    }

    @FXML
    private void handleWaitingList(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/WaitingList.fxml", 1000, 650);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}