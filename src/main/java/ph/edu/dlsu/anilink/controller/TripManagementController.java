package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.DepartureSchedule;
import ph.edu.dlsu.anilink.model.Route;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.List;

/**
 * JavaFX controller for the Trip Management view.
 * <p>
 * This class provides an administrative interface for creating and managing operational trips.
 * It allows administrators to construct a {@link Trip} by associating an existing {@link Route},
 * {@link DepartureSchedule}, and driver (a {@link User} with a driver role). Additionally,
 * it supports updating trip statuses, dynamically reassigning drivers, and deleting trips.
 * All data retrieval and mutation operations are executed asynchronously via {@link SupabaseService}
 * to maintain a responsive user interface. It also handles seamless navigation to other admin
 * modules using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class TripManagementController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper;

    @FXML private Label adminNameLabel;
    @FXML private ComboBox<Route> routeComboBox;
    @FXML private ComboBox<DepartureSchedule> scheduleComboBox;
    @FXML private ComboBox<User> driverComboBox;
    @FXML private TextField capacityField;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<User> updateDriverComboBox;
    @FXML private ComboBox<String> updateStatusComboBox;
    @FXML private ListView<Trip> tripListView;

    private final ObservableList<Trip> trips = FXCollections.observableArrayList();
    private final ObservableList<String> statuses = FXCollections.observableArrayList(
            "SCHEDULED", "ARRIVING", "BOARDING", "IN_TRANSIT", "COMPLETED", "CANCELLED"
    );

    public TripManagementController(SupabaseService supabaseService, UserSession userSession, ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @FXML
    public void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null && adminNameLabel != null) {
            adminNameLabel.setText("Welcome, " + user.getName());
        }

        tripListView.setItems(trips);

        statusComboBox.setItems(statuses);
        statusComboBox.setValue("SCHEDULED");
        updateStatusComboBox.setItems(statuses);

        setupDriverComboBoxConverters();

        loadRoutesAsync();
        loadSchedulesAsync();
        loadDriversAsync();
        loadTripsAsync();
    }

    private void setupDriverComboBoxConverters() {
        StringConverter<User> driverConverter = new StringConverter<>() {
            @Override
            public String toString(User user) {
                return (user != null) ? user.getName() : "";
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        };

        driverComboBox.setConverter(driverConverter);
        updateDriverComboBox.setConverter(driverConverter);
    }

    @FXML
    private void handleRouteSelection() {
        Route selectedRoute = routeComboBox.getValue();
        if (selectedRoute == null) {
            loadSchedulesAsync();
            return;
        }

        Task<List<DepartureSchedule>> task = new Task<>() {
            @Override
            protected List<DepartureSchedule> call() throws Exception {
                String response = supabaseService.getSchedulesByRoute(selectedRoute.getRouteId());
                return objectMapper.readValue(response, new TypeReference<>() {});
            }
        };

        task.setOnSucceeded(e -> scheduleComboBox.getItems().setAll(task.getValue()));
        task.setOnFailed(e -> e.getSource().getException().printStackTrace());

        new Thread(task).start();
    }

    @FXML
    private void handleAddTrip() {
        Route route = routeComboBox.getValue();
        DepartureSchedule schedule = scheduleComboBox.getValue();
        User driver = driverComboBox.getValue();
        String capacityText = capacityField.getText().trim();
        String status = statusComboBox.getValue();

        if (route == null || schedule == null || capacityText.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Information", "Please select a Route, Departure Schedule, and Capacity.");
            return;
        }

        try {
            int capacity = Integer.parseInt(capacityText);
            if (capacity <= 0) {
                showAlert(Alert.AlertType.WARNING, "Invalid Capacity", "Capacity must be greater than zero.");
                return;
            }

            Long driverId = (driver != null) ? driver.getUserId() : null;

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    supabaseService.createTrip(route.getRouteId(), schedule.getScheduleId(), driverId, capacity, status);
                    return null;
                }
            };

            task.setOnSucceeded(e -> {
                loadTripsAsync();
                clearFields();
                showAlert(Alert.AlertType.INFORMATION, "Trip Added", "Trip was successfully created.");
            });

            task.setOnFailed(e -> {
                e.getSource().getException().printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Database Error", "Unable to create trip in Supabase.");
            });

            new Thread(task).start();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Capacity must be a valid number.");
        }
    }

    @FXML
    private void handleAssignDriver() {
        Trip selectedTrip = tripListView.getSelectionModel().getSelectedItem();
        User selectedDriver = updateDriverComboBox.getValue();

        if (selectedTrip == null || selectedDriver == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Selection", "Please select a trip from the list and a driver from the dropdown.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                supabaseService.assignDriverToTrip(selectedTrip.getTripId(), selectedDriver.getUserId());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            loadTripsAsync();
            updateDriverComboBox.getSelectionModel().clearSelection();
            showAlert(Alert.AlertType.INFORMATION, "Driver Assigned", "Successfully assigned " + selectedDriver.getName() + " to the trip.");
        });

        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to assign driver in Supabase.");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleUpdateStatus() {
        Trip selectedTrip = tripListView.getSelectionModel().getSelectedItem();
        String selectedStatus = updateStatusComboBox.getValue();

        if (selectedTrip == null || selectedStatus == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Selection", "Please select a trip from the list and a status from the dropdown.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                supabaseService.updateTripStatus(selectedTrip.getTripId(), selectedStatus);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            loadTripsAsync();
            updateStatusComboBox.getSelectionModel().clearSelection();
            showAlert(Alert.AlertType.INFORMATION, "Trip Updated", "Trip status successfully updated to " + selectedStatus);
        });

        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to update trip status in Supabase.");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleDeleteTrip() {
        Trip selectedTrip = tripListView.getSelectionModel().getSelectedItem();

        if (selectedTrip == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a trip to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete this trip?");
        confirm.setHeaderText("Confirm Deletion");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        supabaseService.deleteTrip(selectedTrip.getTripId());
                        return null;
                    }
                };

                task.setOnSucceeded(e -> {
                    loadTripsAsync();
                    showAlert(Alert.AlertType.INFORMATION, "Trip Deleted", "Trip was successfully deleted.");
                });

                task.setOnFailed(e -> {
                    e.getSource().getException().printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Unable to delete trip. Active reservations may be linked to it.");
                });

                new Thread(task).start();
            }
        });
    }

    /**
     * Syncs seats_taken for the selected trip based on actual active reservations.
     * Fixes mismatched seat counts (e.g., when a cancelled reservation didn't
     * decrement seats_taken).
     */
    @FXML
    private void handleSyncSeats() {
        Trip selectedTrip = tripListView.getSelectionModel().getSelectedItem();

        if (selectedTrip == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a trip from the list to sync seats.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                supabaseService.syncSeatsTaken(selectedTrip.getTripId());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            showAlert(Alert.AlertType.INFORMATION, "Seats Synced", "Seats taken for Trip #" + selectedTrip.getTripId() +
                    " has been recalculated from active reservations.");
            loadTripsAsync();
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            showAlert(Alert.AlertType.ERROR, "Sync Failed", "Failed to sync seats taken.");
        });

        new Thread(task).start();
    }

    // --- Async Data Loaders ---

    private void loadRoutesAsync() {
        Task<List<Route>> task = new Task<>() {
            @Override
            protected List<Route> call() throws Exception {
                String response = supabaseService.getRoutes();
                return objectMapper.readValue(response, new TypeReference<>() {});
            }
        };
        task.setOnSucceeded(e -> routeComboBox.getItems().setAll(task.getValue()));
        task.setOnFailed(e -> e.getSource().getException().printStackTrace());
        new Thread(task).start();
    }

    private void loadSchedulesAsync() {
        Task<List<DepartureSchedule>> task = new Task<>() {
            @Override
            protected List<DepartureSchedule> call() throws Exception {
                String response = supabaseService.getSchedules();
                return objectMapper.readValue(response, new TypeReference<>() {});
            }
        };
        task.setOnSucceeded(e -> scheduleComboBox.getItems().setAll(task.getValue()));
        task.setOnFailed(e -> e.getSource().getException().printStackTrace());
        new Thread(task).start();
    }

    private void loadDriversAsync() {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                String response = supabaseService.getDrivers();
                return objectMapper.readValue(response, new TypeReference<>() {});
            }
        };
        task.setOnSucceeded(e -> {
            List<User> driversList = task.getValue();
            driverComboBox.getItems().setAll(driversList);
            updateDriverComboBox.getItems().setAll(driversList);
        });
        task.setOnFailed(e -> e.getSource().getException().printStackTrace());
        new Thread(task).start();
    }

    private void loadTripsAsync() {
        Task<List<Trip>> task = new Task<>() {
            @Override
            protected List<Trip> call() throws Exception {
                String response = supabaseService.getTripsWithDetails();
                return objectMapper.readValue(response, new TypeReference<>() {});
            }
        };
        task.setOnSucceeded(e -> trips.setAll(task.getValue()));
        task.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Unable to load trips from Supabase.");
        });
        new Thread(task).start();
    }

    private void clearFields() {
        Platform.runLater(() -> {
            capacityField.clear();
            routeComboBox.getSelectionModel().clearSelection();
            scheduleComboBox.getSelectionModel().clearSelection();
            driverComboBox.getSelectionModel().clearSelection();
            statusComboBox.setValue("SCHEDULED");
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

    // --- Navigation Handlers ---

    @FXML
    private void handleDashboard(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/AdminDashboard.fxml", 1100, 700);
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
        // Already on this page
    }

    @FXML
    private void handleRoutes(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/RouteManagement.fxml", 1100, 700);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }
}