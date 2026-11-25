package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.dsa.AniLinkHashMap;
import ph.edu.dlsu.anilink.model.DepartureSchedule;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX controller for the Trip Details view.
 * <p>
 * This class manages the user interface for displaying active trip details assigned to a driver.
 * It handles the presentation of route information, departure schedules, real-time capacity usage,
 * and current trip status. The controller coordinates asynchronous data fetching from the database
 * via {@link SupabaseService} to prevent UI blocking, and manages seamless scene navigation
 * to related modules (like the passenger manifest) using the {@link ViewNavigator}.
 * </p>
 *
 * @author AniLink Development Team
 * @version 1.0
 * @see ph.edu.dlsu.anilink.service.SupabaseService
 * @see ph.edu.dlsu.anilink.util.UserSession
 * @see ph.edu.dlsu.anilink.util.ViewNavigator
 */
@Controller
public class TripDetailsController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label driverNameLabel;
    @FXML private Label tripIdLabel;
    @FXML private Label routeLabel;
    @FXML private Label vehicleTypeLabel;
    @FXML private Label capacityLabel;
    @FXML private Label messageLabel;
    @FXML private Button viewPassengersButton;
    @FXML private Button backButton;
    @FXML private ChoiceBox<String> statusDropdown;
    @FXML private DatePicker dateSelection;
    @FXML private ComboBox<String> scheduleSelection;

    private Long currentTripId;
    private AniLinkHashMap<LocalTime, Trip> assignedTrips;

    public TripDetailsController(SupabaseService supabaseService, UserSession userSession, ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    @FXML
    private void initialize() {
        // Setup User Label
        User user = userSession.getCurrentUser();
        if (user != null && driverNameLabel != null) {
            driverNameLabel.setText("Welcome, " + user.getName());
        }

        Node label = scheduleSelection.lookup(".label");
        if (label != null) label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0d5c36");

        // Setup status dropdown items
        statusDropdown.getItems().addAll("SCHEDULED", "BOARDING", "IN TRANSIT", "COMPLETED");
        statusDropdown.setValue("N/A");
        messageLabel.setVisible(false);

        // Automatic loading
        dateSelection.setValue(LocalDate.now());
        setupListeners();
        loadAssignedTripsAsync(LocalDate.now());
    }

    private void setupListeners() {
        // When date changes, load assigned trips
        dateSelection.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                resetLabels();
                scheduleSelection.setValue(null);
                messageLabel.setVisible(false);
                loadAssignedTripsAsync(newVal);
            }
        });

        // When schedule changes, load trip details
        scheduleSelection.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Trip trip = assignedTrips.get(LocalTime.parse(newVal));
                if (trip != null) {
                    resetLabels();
                    messageLabel.setVisible(false);
                    loadTripDetailsAsync(trip.getTripId());
                }
            }
        });

        // When status changes, update it in Supabase if it is valid
        statusDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            String internalStatus = newVal.replace(" ", "_");
            updateStatusDropdown(internalStatus);

            // Update trip status and passengers status (except N/A)
            if (oldVal != null && !newVal.isBlank() && !newVal.equals("N/A")) {
                supabaseService.updateTripStatus(currentTripId, internalStatus.toUpperCase());
                updatePassengersStatus(internalStatus.toUpperCase());

                if (!oldVal.equals("N/A")) {
                    showMessage("Updated status from " + oldVal + " to " + newVal + ".", "#15803D");
                }
            }
        });
    }

    private void updatePassengersStatus(String status) {
        Task<List<Long>> task = new Task<>() {
            @Override
            protected List<Long> call() throws Exception {
                String json = supabaseService.getReservationsByTripId(currentTripId);
                JsonNode reservations = objectMapper.readTree(json);
                List<Long> ids = new ArrayList<>();

                if (reservations.isArray() && !reservations.isEmpty()) {
                    for (JsonNode node : reservations) {
                        long id = node.path("id").asLong();
                        String currentStatus = node.path("status").asText();

                        if (!currentStatus.equalsIgnoreCase("CANCELLED")) {
                            ids.add(id);
                        }
                    }
                    return ids;
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                for (long id : task.getValue()) {
                    supabaseService.updateReservationStatus(id, status);
                }
            }
        });

        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            resetLabels();
        });

        new Thread(task).start();
    }

    public void setTripId(Long tripId) {
        this.currentTripId = tripId;
        loadTripDetailsAsync(tripId);
    }

    private void loadAssignedTripsAsync(LocalDate date) {
        User user = userSession.getCurrentUser();
        if (user == null) {
            resetLabels();
            return;
        }
        assignedTrips = new AniLinkHashMap<>();

        Task<ObservableList<String>> task = new Task<>() {
            @Override
            protected ObservableList<String> call() throws Exception {
                String dateStr = date != null ? date.toString() : null;
                String json = supabaseService.getTripsByDriver(user.getUserId());
                JsonNode trips = objectMapper.readTree(json);

                if (trips.isArray() && !trips.isEmpty()) {
                    ObservableList<String> departureTimes = FXCollections.observableArrayList();

                    for (JsonNode trip : trips) {
                        long id = trip.path("id").asLong();
                        String status = trip.path("status").asText("N/A");

                        JsonNode tripSchedule = trip.path("schedule");
                        String schedDate = tripSchedule.path("departure_date").asText("N/A");
                        String time = tripSchedule.path("departure_time").asText("N/A");

                        if (schedDate.equals(dateStr)) {
                            Trip tripObj = new Trip();
                            tripObj.setTripId(id);
                            tripObj.setStatus(status);

                            DepartureSchedule ds = new DepartureSchedule();
                            ds.setDepartureDate(LocalDate.parse(schedDate));
                            ds.setDepartureTime(LocalTime.parse(time));
                            tripObj.setSchedule(ds);

                            assignedTrips.put(LocalTime.parse(time), tripObj);
                            departureTimes.add(time);
                        }
                    }
                    return departureTimes;
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            // Setup departure dropdown items and initial value
            if (task.getValue() != null && !task.getValue().isEmpty()) {
                SortedList<String> sortedTimes = task.getValue().sorted();
                scheduleSelection.setItems(sortedTimes);
                scheduleSelection.setValue(sortedTimes.getFirst());
            } else {
                showMessage("No assigned trips on this date.", "#DC2626");
            }
        });

        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            resetLabels();
        });

        new Thread(task).start();
    }

    private void loadTripDetailsAsync(Long tripId) {
        Task<JsonNode> task = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                String json = supabaseService.getTripDetails(tripId);
                JsonNode array = objectMapper.readTree(json);
                if (array.isArray() && !array.isEmpty()) {
                    return array.get(0);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            JsonNode trip = task.getValue();
            if (trip != null) {
                this.currentTripId = trip.path("id").asLong();

                String status = trip.path("status").asText("SCHEDULED");
                int capacity = trip.path("capacity").asInt(0);
                int seatsTaken = trip.path("seats_taken").asInt(0);
                String vehicleType = trip.path("vehicle_type").asText("Unknown");

                // Resilient lookup: check alias "route" first, fallback to "routes"
                JsonNode route = trip.has("route") ? trip.path("route") : trip.path("routes");
                String origin = route.path("origin").asText("Unknown");
                String destination = route.path("destination").asText("Unknown");

                tripIdLabel.setText("#" + currentTripId);
                routeLabel.setText(origin + " ↔ " + destination);
                vehicleTypeLabel.setText(vehicleType);
                capacityLabel.setText(String.format("%d / %d", seatsTaken, capacity));

                statusDropdown.setValue(status.replace("_", " "));
                updateStatusDropdown(status);
                viewPassengersButton.setDisable(false);
            } else {
                resetLabels();
            }
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace(); // Prints stacktrace to console for easy debugging
            }
            showAlert(Alert.AlertType.ERROR, "Database Error", "Unable to load trip details.");
        });

        new Thread(task).start();
    }

    private void updateStatusDropdown(String status) {
        Node label = statusDropdown.lookup(".label");

        String baseStyle = "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-border-width: 1px; -fx-border-radius: 12px;";
        switch (status.toUpperCase()) {
            case "SCHEDULED":
                statusDropdown.setStyle(baseStyle + "-fx-background-color: #DBEAFE; -fx-border-color: #1D4ED8; -fx-mark-color: #1D4ED8;");
                if (label != null) label.setStyle("-fx-text-fill: #1D4ED8;");
                break;
            case "BOARDING":
                statusDropdown.setStyle(baseStyle + "-fx-background-color: #e3d1fd; -fx-border-color: #5f0fdc; -fx-mark-color: #5f0fdc;");
                if (label != null) label.setStyle("-fx-text-fill: #5f0fdc;");
                break;
            case "IN_TRANSIT":
                statusDropdown.setStyle(baseStyle + "-fx-background-color: #FEF3C7; -fx-border-color: #B45309; -fx-mark-color: #B45309;");
                if (label != null) label.setStyle("-fx-text-fill: #B45309;");
                break;
            case "COMPLETED":
                statusDropdown.setStyle(baseStyle + "-fx-background-color: #DCFCE7; -fx-border-color: #15803D; -fx-mark-color: #15803D;");
                if (label != null) label.setStyle("-fx-text-fill: #15803D;");
                break;
            default:
                statusDropdown.setStyle(baseStyle + "-fx-background-color: #E2E8F0; -fx-border-color: #475569; -fx-mark-color: #475569;");
                if (label != null) label.setStyle("-fx-text-fill: #475569;");
                break;
        }
    }

    private void resetLabels() {
        Platform.runLater(() -> {
            tripIdLabel.setText("--");
            routeLabel.setText("No active trip");
            vehicleTypeLabel.setText("--");
            capacityLabel.setText("0 / 0");
            statusDropdown.setValue("N/A");
            viewPassengersButton.setDisable(true);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showMessage(String message, String colorCode) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: " + colorCode + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        messageLabel.setVisible(true);
    }

    // --- Navigation Handlers ---

    @FXML
    private void handleViewPassengers(ActionEvent event) {
        if (currentTripId == null) {
            showAlert(Alert.AlertType.WARNING, "No Trip Selected", "Please wait for a valid trip to load.");
            return;
        }

        // Load view and get controller instance directly
        PassengerListController controller = viewNavigator.navigateToAndGetController(
                event,
                "/fxml/PassengerList.fxml",
                1000,
                650
        );

        if (controller != null) {
            controller.setTripId(currentTripId);
        }
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/DriverDashboard.fxml", 1000, 650);
    }

    @FXML
    private void handleTripDetails(ActionEvent event) {
        // Already on this page
    }

    @FXML
    private void handleScanQR(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/QRScanner.fxml", 1000, 650);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }
}