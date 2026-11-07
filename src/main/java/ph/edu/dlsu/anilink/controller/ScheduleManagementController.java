package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.core.type.TypeReference;
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
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.ScheduleBubbleSort;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Schedule Management Controller
 *
 * feat: Implement departure schedule creation,
 * schedule selection dropdowns, route filtering,
 * and chronological sorting using Bubble Sort.
 *
 * Existing Supabase and navigation features are retained.
 */
@Controller
public class ScheduleManagementController {

    @FXML private Label adminNameLabel;
    @FXML private ComboBox<Route> routeComboBox;
    // feat: Dropdown for selecting or entering departure time.
    @FXML private ComboBox<String> departureTimeComboBox;
    // feat: Dropdown for selecting existing schedules.
    @FXML private ComboBox<DepartureSchedule> existingScheduleComboBox;
    @FXML private TextField capacityField;
    @FXML private ListView<DepartureSchedule> scheduleListView;
    @FXML private Label statusLabel;

    // feat: Shared schedule collection for list and dropdown.
    private final ObservableList<DepartureSchedule> schedules =
            FXCollections.observableArrayList();
    // feat: Prevent outdated requests from replacing current results.
    private long latestScheduleRequest = 0;

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper;

    public ScheduleManagementController(
            SupabaseService supabaseService,
            UserSession userSession,
            ViewNavigator viewNavigator) {

        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @FXML
    public void initialize() {

        User user = userSession.getCurrentUser();

        if (user != null && adminNameLabel != null) {
            adminNameLabel.setText("Welcome, " + user.getName());
        }

        setupCustomFormatters();

        scheduleListView.setItems(schedules);
        existingScheduleComboBox.setItems(schedules);

        // feat: Initialize departure time dropdown.
        setupDepartureTimeDropdown();

        // feat: Synchronize ListView and ComboBox selection.
        scheduleListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> {

                    if (selected == null) {

                        existingScheduleComboBox
                                .getSelectionModel()
                                .clearSelection();

                    } else if (
                            existingScheduleComboBox.getValue() != selected) {

                        existingScheduleComboBox.setValue(selected);
                    }
                });

        // EXISTING: Load routes and schedules from Supabase.
        loadRoutesAsync();
        loadSchedulesAsync();
    }

    /**
     * feat: Generate departure times every 30 minutes.
     *
     * Administrators can choose a predefined time
     * or enter a custom time using HH:mm.
     */
    private void setupDepartureTimeDropdown() {

        departureTimeComboBox.setEditable(true);

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("HH:mm");

        for (int hour = 0; hour < 24; hour++) {

            for (int minute = 0; minute < 60; minute += 30) {

                LocalTime time = LocalTime.of(hour, minute);

                departureTimeComboBox.getItems().add(
                        time.format(formatter)
                );
            }
        }
    }

    private void setupCustomFormatters() {

        StringConverter<Route> routeConverter =
                new StringConverter<>() {

                    @Override
                    public String toString(Route route) {

                        if (route == null) {
                            return "";
                        }

                        return route.getOrigin()
                                + " ↔ "
                                + route.getDestination();
                    }

                    @Override
                    public Route fromString(String text) {
                        return null;
                    }
                };

        routeComboBox.setConverter(routeConverter);

        routeComboBox.setCellFactory(param -> new ListCell<>() {

            @Override
            protected void updateItem(Route item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);

                } else {

                    setText(item.getOrigin()
                            + " ↔ "
                            + item.getDestination());
                }
            }
        });

        existingScheduleComboBox.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(DepartureSchedule schedule) {

                        return schedule == null
                                ? ""
                                : schedule.toString();
                    }

                    @Override
                    public DepartureSchedule fromString(String text) {
                        return null;
                    }
                }
        );

        scheduleListView.setCellFactory(param -> new ListCell<>() {

            @Override
            protected void updateItem(
                    DepartureSchedule item,
                    boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {

                    setText(null);

                } else {

                    String routeName = item.getRoute() != null
                            ? item.getRoute().getOrigin()
                              + " ↔ "
                              + item.getRoute().getDestination()
                            : "Unknown Route";

                    String time = item.getDepartureTime() == null
                            ? "N/A"
                            : item.getDepartureTime().format(
                            DateTimeFormatter.ofPattern("hh:mm a")
                    );

                    setText(String.format(
                            "[%s]  %s  |  Capacity: %d",
                            time,
                            routeName,
                            item.getCapacity()
                    ));
                }
            }
        });
    }

    private void loadRoutesAsync() {

        Task<List<Route>> task = new Task<>() {

            @Override
            protected List<Route> call() throws Exception {

                String response = supabaseService.getRoutes();

                return objectMapper.readValue(
                        response,
                        new TypeReference<List<Route>>() {}
                );
            }
        };

        task.setOnSucceeded(e -> {

            routeComboBox.getItems().setAll(task.getValue());
        });

        task.setOnFailed(e -> {

            e.getSource().getException().printStackTrace();

            showAlert(
                    Alert.AlertType.ERROR,
                    "Database Error",
                    "Unable to load routes."
            );
        });

        Thread worker = new Thread(task, "anilink-load-routes");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * feat: Retrieve schedules from Supabase.
     * feat: Apply Bubble Sort by departure time.
     * feat: Filter schedules according to selected route.
     */
    private void loadSchedulesAsync() {

        Route selectedRoute = routeComboBox.getValue();

        Long routeId = selectedRoute == null
                ? null
                : selectedRoute.getRouteId();

        long requestId = ++latestScheduleRequest;

        Task<List<DepartureSchedule>> task = new Task<>() {

            @Override
            protected List<DepartureSchedule> call() throws Exception {

                String response;

                if (routeId == null) {

                    response = supabaseService.getSchedules();

                } else {

                    response = supabaseService
                            .getSchedulesByRoute(routeId);
                }

                List<DepartureSchedule> loadedSchedules =
                        objectMapper.readValue(
                                response,
                                new TypeReference<List<DepartureSchedule>>() {}
                        );

                // feat: Apply Bubble Sort (earliest to latest).
                return ScheduleBubbleSort.byDepartureTime(
                        loadedSchedules
                );
            }
        };

        task.setOnSucceeded(e -> {

            Route currentRoute = routeComboBox.getValue();

            Long currentRouteId = currentRoute == null
                    ? null
                    : currentRoute.getRouteId();

            if (requestId == latestScheduleRequest
                    && Objects.equals(routeId, currentRouteId)) {

                schedules.setAll(task.getValue());
            }
        });

        task.setOnFailed(e -> {

            if (requestId == latestScheduleRequest) {

                e.getSource().getException().printStackTrace();

                showAlert(
                        Alert.AlertType.ERROR,
                        "Database Error",
                        "Unable to load schedules."
                );
            }
        });

        Thread worker = new Thread(task, "anilink-load-schedules");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleScheduleDropdownSelection() {

        DepartureSchedule selectedSchedule =
                existingScheduleComboBox.getValue();

        if (selectedSchedule != null) {

            scheduleListView
                    .getSelectionModel()
                    .select(selectedSchedule);

            scheduleListView.scrollTo(selectedSchedule);
        }
    }

    /**
     * feat: Create schedules using dropdown-selected
     * or custom departure times.
     *
     * Existing Supabase creation logic is retained.
     */
    @FXML
    private void handleAddSchedule() {

        Route route = routeComboBox.getValue();

        String departureTimeText =
                departureTimeComboBox.getEditor()
                        .getText()
                        .trim();

        String capacityText =
                capacityField.getText().trim();

        if (route == null
                || departureTimeText.isEmpty()
                || capacityText.isEmpty()) {

            showInlineStatus(
                    "Please fill in all fields.",
                    "#DC2626"
            );

            return;
        }

        try {

            DateTimeFormatter inputFormatter =
                    DateTimeFormatter.ofPattern("H:mm");

            LocalTime parsedTime = LocalTime.parse(
                    departureTimeText,
                    inputFormatter
            );

            String dbFormattedTime = parsedTime.format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")
            );

            int capacity = Integer.parseInt(capacityText);

            if (capacity <= 0) {

                showInlineStatus(
                        "Capacity must be greater than 0.",
                        "#DC2626"
                );

                return;
            }

            showInlineStatus(
                    "Adding schedule...",
                    "#0284C7"
            );

            Task<Void> task = new Task<>() {

                @Override
                protected Void call() throws Exception {

                    // EXISTING: Save the schedule to Supabase.
                    supabaseService.createSchedule(
                            route.getRouteId(),
                            dbFormattedTime,
                            capacity
                    );

                    return null;
                }
            };

            task.setOnSucceeded(e -> {

                showInlineStatus(
                        "Schedule created successfully!",
                        "#16A34A"
                );

                clearFields();

                // feat: Reload and Bubble Sort after creation.
                loadSchedulesAsync();
            });

            task.setOnFailed(e -> {

                Throwable error = e.getSource().getException();

                error.printStackTrace();

                showInlineStatus(
                        "Failed to create schedule.",
                        "#DC2626"
                );

                showAlert(
                        Alert.AlertType.ERROR,
                        "Database Error",
                        "Supabase rejected the request. Reason:\n"
                                + error.getMessage()
                );
            });

            Thread worker =
                    new Thread(task, "anilink-create-schedule");

            worker.setDaemon(true);
            worker.start();

        } catch (NumberFormatException e) {

            showInlineStatus(
                    "Capacity must be a number.",
                    "#DC2626"
            );

        } catch (DateTimeParseException e) {

            showInlineStatus(
                    "Use 24h time format (e.g., 08:30 or 14:00).",
                    "#DC2626"
            );
        }
    }

    @FXML
    private void handleRouteSelection() {

        loadSchedulesAsync();
    }

    @FXML
    private void handleDeleteSchedule() {

        DepartureSchedule selectedSchedule =
                scheduleListView.getSelectionModel()
                        .getSelectedItem();

        if (selectedSchedule == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "No Selection",
                    "Please select a schedule to delete."
            );

            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);

        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Schedule");

        confirm.setContentText(
                "Are you sure you want to delete this schedule?\n"
                        + "Time: "
                        + selectedSchedule.getDepartureTime()
        );

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent()
                && result.get() == ButtonType.OK) {

            Task<Void> task = new Task<>() {

                @Override
                protected Void call() throws Exception {

                    supabaseService.deleteSchedule(
                            selectedSchedule.getScheduleId()
                    );

                    return null;
                }
            };

            task.setOnSucceeded(e -> {

                showInlineStatus(
                        "Schedule deleted.",
                        "#16A34A"
                );

                // feat: Refresh sorted schedules after deletion.
                loadSchedulesAsync();
            });

            task.setOnFailed(e -> {

                e.getSource().getException().printStackTrace();

                showAlert(
                        Alert.AlertType.ERROR,
                        "Database Error",
                        "Unable to delete schedule. "
                                + "It may be linked to active trips."
                );
            });

            Thread worker =
                    new Thread(task, "anilink-delete-schedule");

            worker.setDaemon(true);
            worker.start();
        }
    }

    /**
     * feat: Clear entered values after schedule creation.
     * Keep the route selected for consecutive schedule creation.
     */
    private void clearFields() {

        departureTimeComboBox.setValue(null);
        departureTimeComboBox.getEditor().clear();

        capacityField.clear();
    }

    private void showInlineStatus(
            String message,
            String colorCode) {

        Platform.runLater(() -> {

            statusLabel.setText(message);

            statusLabel.setStyle(
                    "-fx-text-fill: " + colorCode
                            + "; -fx-font-weight: bold;"
                            + " -fx-font-size: 13px;"
            );
        });
    }

    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Platform.runLater(() -> {

            Alert alert = new Alert(type);

            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.showAndWait();
        });
    }

    @FXML
    private void handleDashboard(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/AdminDashboard.fxml",
                1000,
                650
        );
    }

    @FXML
    private void handleRouteManagement(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/RouteManagement.fxml",
                1000,
                650
        );
    }

    @FXML
    private void handleScheduleManagement(ActionEvent event) {

        // Already on Schedule Management.
    }

    @FXML
    private void handleTripManagement(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/TripManagement.fxml",
                1000,
                650
        );
    }

    @FXML
    private void handleLogout(ActionEvent event) {

        userSession.clearSession();

        viewNavigator.navigateTo(
                event,
                "/fxml/Login.fxml",
                900,
                600
        );
    }
}