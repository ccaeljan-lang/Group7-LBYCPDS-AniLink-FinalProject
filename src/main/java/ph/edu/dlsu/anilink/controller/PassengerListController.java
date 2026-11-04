package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX controller for the Passenger List (Manifest) view.
 * <p>
 * This class manages the user interface for displaying the passenger manifest
 * of a specific trip. It is typically accessed by driver or administrative users.
 * The controller asynchronously retrieves reservation data for a dynamically assigned
 * trip ID using {@link SupabaseService}, filters and formats the passenger details,
 * and safely populates a JavaFX {@link ListView} on the UI thread. It also provides
 * navigation handlers via the {@link ViewNavigator} to return to the trip details,
 * access the QR scanner, or navigate back to the dashboard.
 * </p>
 */
@Controller
public class PassengerListController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long activeTripId;

    @FXML private Label driverNameLabel;
    @FXML private Label tripInfoLabel;
    @FXML private ListView<String> passengerListView;
    @FXML private Button backButton;

    /**
     * Constructs the PassengerListController with required services.
     *
     * @param supabaseService service for database operations
     * @param userSession current user session state
     * @param viewNavigator utility for scene transitions
     */
    public PassengerListController(SupabaseService supabaseService,
                                   UserSession userSession,
                                   ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    /**
     * Initializes the view and sets welcome text for the logged-in user.
     */
    @FXML
    private void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null && driverNameLabel != null) {
            driverNameLabel.setText("Welcome, " + user.getName());
        }

        if (this.activeTripId != null) {
            loadPassengersForTripAsync(this.activeTripId);
        }
    }

    /**
     * Sets the target trip ID and triggers loading of reserved passengers.
     *
     * @param tripId unique ID of the selected trip
     */
    public void setTripId(Long tripId) {
        this.activeTripId = tripId;
        if (tripInfoLabel != null) {
            tripInfoLabel.setText("Passenger Manifest — Trip #" + tripId);
        }
        loadPassengersForTripAsync(tripId);
    }

    /**
     * Asynchronously retrieves reserved passengers for the given trip ID from Supabase.
     *
     * @param tripId unique ID of the selected trip
     */
    private void loadPassengersForTripAsync(Long tripId) {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> passengersList = new ArrayList<>();

                String json = supabaseService.getReservationsByTripId(tripId);
                JsonNode reservationsArray = objectMapper.readTree(json);

                if (reservationsArray.isArray() && !reservationsArray.isEmpty()) {
                    int count = 1;
                    for (JsonNode reservation : reservationsArray) {
                        String status = reservation.path("status").asText("CONFIRMED");

                        if ("CANCELLED".equalsIgnoreCase(status)) {
                            continue;
                        }

                        JsonNode passengerNode = reservation.has("passenger")
                                ? reservation.path("passenger")
                                : reservation.path("user");

                        if (passengerNode.isMissingNode() || passengerNode.isNull()) {
                            continue;
                        }

                        long passengerId = passengerNode.path("id").asLong();
                        String name = passengerNode.path("name").asText("Unknown");
                        String email = passengerNode.path("email").asText("N/A");
                        String category = passengerNode.path("category").asText("PASSENGER");

                        String displayText = String.format("%d. #%d | %s (%s) — [%s] | Status: %s",
                                count++, passengerId, name, email, category, status.toUpperCase());
                        passengersList.add(displayText);
                    }
                }

                if (passengersList.isEmpty()) {
                    passengersList.add("No reserved passengers found for Trip #" + tripId + ".");
                }

                return passengersList;
            }
        };

        task.setOnSucceeded(e -> {
            passengerListView.getItems().clear();
            passengerListView.getItems().addAll(task.getValue());
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            showAlert("Database Error", "Failed to retrieve passenger manifest from Supabase.");
        });

        new Thread(task).start();
    }

    /**
     * Navigates to the Driver Dashboard scene.
     *
     * @param event button action event
     */
    @FXML
    private void handleDashboard(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/DriverDashboard.fxml", 1000, 650);
    }

    /**
     * Navigates to the Trip Details scene.
     *
     * @param event button action event
     */
    @FXML
    private void handleTripDetails(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/TripDetails.fxml", 1000, 650);
    }

    /**
     * Navigates to the QR Scanner scene.
     *
     * @param event button action event
     */
    @FXML
    private void handleScanQR(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/QRScanner.fxml", 1000, 650);
    }

    /**
     * Logs out the user and navigates to the Login scene.
     *
     * @param event button action event
     */
    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    /**
     * Navigates back to the Trip Details scene.
     *
     * @param event button action event
     */
    @FXML
    private void handleBack(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/TripDetails.fxml", 1000, 650);
    }

    /**
     * Displays an error alert dialog on the JavaFX Application Thread.
     *
     * @param title dialog title
     * @param message error message content
     */
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