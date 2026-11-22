package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

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
import ph.edu.dlsu.anilink.dsa.ScheduleBubbleSort;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ScheduleManagementController extends AbstractAdminController {

    @FXML private Label adminNameLabel;
    @FXML private ComboBox<Route> routeComboBox;
    @FXML private ComboBox<String> departureTimeComboBox;
    @FXML private ComboBox<DepartureSchedule> existingScheduleComboBox;
    @FXML private TextField capacityField;
    @FXML private ListView<DepartureSchedule> scheduleListView;
    @FXML private Label statusLabel;
    @FXML private Button createScheduleButton;
    @FXML private DatePicker departureDatePicker;
    @FXML private CheckBox repeatWeekCheckBox;

    private final ObservableList<DepartureSchedule> schedules =
            FXCollections.observableArrayList();

    private long latestScheduleRequest = 0;
    private boolean creatingSchedule = false;

    public ScheduleManagementController(
            SupabaseService supabaseService,
            UserSession userSession,
            ViewNavigator viewNavigator) {

        super(supabaseService, userSession, viewNavigator);
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

        setupDepartureTimeDropdown();

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

        loadRoutesAsync();
        loadSchedulesAsync();
    }

    /**
     * Generate departure times every 30 minutes.
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

            task.getException().printStackTrace();

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
     * Retrieve schedules from Supabase.
     * Apply Bubble Sort by departure time.
     * Filter schedules according to selected route.
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

                task.getException().printStackTrace();

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

    @FXML
    private void handleAddSchedule() {

        Route route = routeComboBox.getValue();

        // FIX: Assign the date only once so it is effectively final.
        final LocalDate departureDate =
                departureDatePicker.getValue();

        String departureTimeText =
                departureTimeComboBox.getEditor()
                        .getText()
                        .trim();

        String capacityText =
                capacityField.getText().trim();

        if (route == null
                || departureDate == null
                || departureTimeText.isEmpty()
                || capacityText.isEmpty()) {

            showInlineStatus(
                    statusLabel,
                    "Please fill in all fields.",
                    "#DC2626"
            );

            return;
        }

        if (departureDate.isBefore(LocalDate.now())) {

            showInlineStatus(
                    statusLabel,
                    "Please select a future date.",
                    "#DC2626"
            );

            return;
        }

        if (creatingSchedule) {

            showInlineStatus(
                    statusLabel,
                    "Please wait, a schedule is being created.",
                    "#0284C7"
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
                        statusLabel,
                        "Capacity must be greater than 0.",
                        "#DC2626"
                );

                return;
            }

            boolean repeatWeek =
                    repeatWeekCheckBox != null
                            && repeatWeekCheckBox.isSelected();

            showInlineStatus(
                    statusLabel,
                    repeatWeek
                            ? "Creating weekly schedules..."
                            : "Adding schedule...",
                    "#0284C7"
            );

            creatingSchedule = true;

            if (createScheduleButton != null) {
                createScheduleButton.setDisable(true);
            }

            Task<String> task = new Task<>() {

                @Override
                protected String call() throws Exception {

                    if (repeatWeek) {

                        String groupId = UUID.randomUUID().toString();

                        LocalDate monday = departureDate
                                .with(TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY
                                ));

                        int created = 0;
                        int skipped = 0;

                        java.util.List<String> skippedDates =
                                new java.util.ArrayList<>();

                        for (int day = 0; day < 6; day++) {

                            LocalDate currentDate =
                                    monday.plusDays(day);

                            String currentDateStr =
                                    currentDate.toString();

                            if (supabaseService.scheduleExists(
                                    route.getRouteId(),
                                    currentDateStr,
                                    dbFormattedTime)) {

                                skipped++;

                                skippedDates.add(currentDateStr);

                                continue;
                            }

                            supabaseService.createSchedule(
                                    route.getRouteId(),
                                    currentDateStr,
                                    dbFormattedTime,
                                    capacity,
                                    true,
                                    groupId
                            );

                            created++;
                        }

                        if (created == 0) {

                            return "DUPLICATE_ALL";

                        } else if (skipped > 0) {

                            return "PARTIAL:"
                                    + created + ":"
                                    + skipped + ":"
                                    + String.join(",", skippedDates);
                        }

                        return "SUCCESS:" + created + ":" + groupId;

                    } else {

                        String dbFormattedDate =
                                departureDate.toString();

                        if (supabaseService.scheduleExists(
                                route.getRouteId(),
                                dbFormattedDate,
                                dbFormattedTime)) {

                            return "DUPLICATE";
                        }

                        supabaseService.createSchedule(
                                route.getRouteId(),
                                dbFormattedDate,
                                dbFormattedTime,
                                capacity
                        );

                        return "SUCCESS:1:"
                                + departureDate.toString();
                    }
                }
            };

            task.setOnSucceeded(e -> {

                creatingSchedule = false;

                if (createScheduleButton != null) {
                    createScheduleButton.setDisable(false);
                }

                String outcome = task.getValue();

                if ("DUPLICATE".equals(outcome)) {

                    showInlineStatus(
                            statusLabel,
                            "A schedule for this route, date, and time already exists.",
                            "#DC2626"
                    );

                    showAlert(
                            Alert.AlertType.WARNING,
                            "Duplicate Schedule",
                            "A departure schedule for this route on "
                                    + departureDate
                                    + " at " + dbFormattedTime
                                    + " already exists. "
                                    + "Please choose a different date, time, or route."
                    );

                    return;
                }

                if ("DUPLICATE_ALL".equals(outcome)) {

                    showInlineStatus(
                            statusLabel,
                            "All weekdays already have this schedule.",
                            "#DC2626"
                    );

                    showAlert(
                            Alert.AlertType.WARNING,
                            "Duplicate Schedule",
                            "Every day Monday–Saturday already has a schedule for this route "
                                    + "at " + dbFormattedTime + "."
                    );

                    return;
                }

                if (outcome != null
                        && outcome.startsWith("PARTIAL:")) {

                    String[] parts = outcome.split(":");

                    int created = Integer.parseInt(parts[1]);
                    int skipped = Integer.parseInt(parts[2]);

                    String skippedDates =
                            parts.length > 3 ? parts[3] : "";

                    showInlineStatus(
                            statusLabel,
                            "Created " + created
                                    + " of 6 weekly schedules ("
                                    + skipped + " already existed).",
                            "#16A34A"
                    );

                    showAlert(
                            Alert.AlertType.INFORMATION,
                            "Weekly Schedule Created (Partial)",
                            "Created " + created
                                    + " schedule(s) for this route at "
                                    + dbFormattedTime + ".\n"
                                    + skipped
                                    + " day(s) already had a schedule and were skipped.\n"
                                    + (skippedDates.isEmpty()
                                    ? ""
                                    : "Skipped dates: " + skippedDates)
                    );

                    clearFields();
                    loadSchedulesAsync();

                    return;
                }

                if (outcome != null
                        && outcome.startsWith("SUCCESS:")) {

                    String[] parts = outcome.split(":");

                    int created = Integer.parseInt(parts[1]);

                    if (repeatWeek) {

                        showInlineStatus(
                                statusLabel,
                                "Created " + created
                                        + " weekly schedules (Mon–Sat).",
                                "#16A34A"
                        );

                        showAlert(
                                Alert.AlertType.INFORMATION,
                                "Weekly Schedule Created",
                                "Created " + created
                                        + " departure schedule(s) for this route at "
                                        + dbFormattedTime
                                        + ", Monday through Saturday."
                        );

                    } else {

                        showInlineStatus(
                                statusLabel,
                                "Schedule created successfully!",
                                "#16A34A"
                        );

                        showAlert(
                                Alert.AlertType.INFORMATION,
                                "Schedule Created",
                                "A departure schedule for this route on "
                                        + departureDate
                                        + " at " + dbFormattedTime
                                        + " has been created."
                        );
                    }

                    clearFields();
                    loadSchedulesAsync();

                    return;
                }

                showInlineStatus(
                        statusLabel,
                        "Schedule created successfully!",
                        "#16A34A"
                );

                clearFields();
                loadSchedulesAsync();
            });

            task.setOnFailed(e -> {

                creatingSchedule = false;

                if (createScheduleButton != null) {
                    createScheduleButton.setDisable(false);
                }

                // FIX: Retrieve the exception directly from the Task.
                Throwable error = task.getException();

                if (error != null) {
                    error.printStackTrace();
                }

                showInlineStatus(
                        statusLabel,
                        "Failed to create schedule.",
                        "#DC2626"
                );

                showAlert(
                        Alert.AlertType.ERROR,
                        "Database Error",
                        "Supabase rejected the request. Reason:\n"
                                + (error != null
                                ? error.getMessage()
                                : "Unknown error")
                );
            });

            Thread worker =
                    new Thread(task, "anilink-create-schedule");

            worker.setDaemon(true);
            worker.start();

        } catch (NumberFormatException e) {

            showInlineStatus(
                    statusLabel,
                    "Capacity must be a number.",
                    "#DC2626"
            );

        } catch (DateTimeParseException e) {

            showInlineStatus(
                    statusLabel,
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
                        statusLabel,
                        "Schedule deleted.",
                        "#16A34A"
                );

                loadSchedulesAsync();
            });

            task.setOnFailed(e -> {

                // FIX: Retrieve the exception directly from the Task.
                Throwable error = task.getException();

                if (error != null) {
                    error.printStackTrace();
                }

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
     * Clear entered values after schedule creation.
     * Keep the route selected for consecutive schedule creation.
     */
    private void clearFields() {

        if (departureDatePicker != null) {
            departureDatePicker.setValue(null);
        }

        departureTimeComboBox.setValue(null);
        departureTimeComboBox.getEditor().clear();

        capacityField.clear();
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        navigateToDashboard(event, 1000, 650);
    }

    @FXML
    private void handleRouteManagement(ActionEvent event) {
        navigateToRouteManagement(event, 1000, 650);
    }

    @FXML
    private void handleScheduleManagement(ActionEvent event) {
        // Already on Schedule Management.
    }

    @FXML
    private void handleTripManagement(ActionEvent event) {
        navigateToTripManagement(event, 1000, 650);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        navigateToLogout(event, 900, 600);
    }
}
