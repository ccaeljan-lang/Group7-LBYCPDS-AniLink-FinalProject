package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.model.WaitingList;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.service.WaitingListService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX controller for the passenger's Waiting List view.
 * Loads the current user's waitlist entries without blocking the JavaFX thread.
 */
@Controller
public class WaitingListController {

    private static final Logger LOGGER =
            Logger.getLogger(WaitingListController.class.getName());

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WaitingListService waitingListService;

    @FXML private Label passengerNameLabel;
    @FXML private TableView<WaitlistItem> waitlistTable;
    @FXML private TableColumn<WaitlistItem, String> waitlistCodeColumn;
    @FXML private TableColumn<WaitlistItem, String> routeColumn;
    @FXML private TableColumn<WaitlistItem, String> dateColumn;
    @FXML private TableColumn<WaitlistItem, String> departureColumn;
    @FXML private TableColumn<WaitlistItem, String> statusColumn;
    @FXML private TableColumn<WaitlistItem, String> queuePosColumn;

    private final ObservableList<WaitlistItem> waitlistList =
            FXCollections.observableArrayList();

    public WaitingListController(SupabaseService supabaseService,
                                 UserSession userSession,
                                 ViewNavigator viewNavigator,
                                 WaitingListService waitingListService) {

        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.waitingListService = waitingListService;
    }

    @FXML
    private void initialize() {

        User currentUser = userSession.getCurrentUser();

        if (currentUser != null && passengerNameLabel != null) {
            passengerNameLabel.setText("Welcome, " + currentUser.getName());
        }

        setupTableColumns();
        loadWaitlistsAsync();
    }

    private void setupTableColumns() {

        waitlistCodeColumn.setCellValueFactory(
                new PropertyValueFactory<>("waitlistCode"));

        routeColumn.setCellValueFactory(
                new PropertyValueFactory<>("route"));

        dateColumn.setCellValueFactory(
                new PropertyValueFactory<>("date"));

        departureColumn.setCellValueFactory(
                new PropertyValueFactory<>("departureTime"));

        statusColumn.setCellValueFactory(
                new PropertyValueFactory<>("status"));

        queuePosColumn.setCellValueFactory(
                new PropertyValueFactory<>("queuePos"));

        waitlistTable.setItems(waitlistList);
    }

    private void loadWaitlistsAsync() {

        User currentUser = userSession.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        Task<List<WaitlistItem>> task = new Task<>() {

            @Override
            protected List<WaitlistItem> call() throws Exception {

                List<WaitlistItem> fetchedItems = new ArrayList<>();

                String json = supabaseService.getWaitingListByPassenger(
                        currentUser.getUserId());

                JsonNode array = objectMapper.readTree(json);

                if (array != null && array.isArray()) {

                    for (JsonNode node : array) {

                        try {

                            long id = node.path("id").asLong();

                            String status = node.path("status")
                                    .asText(WaitingList.STATUS_WAITING);

                            String joinedAt = node.path("joined_at")
                                    .asText("N/A").split("T")[0];

                            JsonNode trip = node.path("trip");
                            JsonNode route = trip.path("route");
                            JsonNode schedule = trip.path("schedule");

                            String origin = route.path("origin")
                                    .asText("Unknown");

                            String destination = route.path("destination")
                                    .asText("Unknown");

                            String routeText = origin + " ↔ " + destination;

                            String departureTime = schedule
                                    .path("departure_time")
                                    .asText("N/A");

                            // Display the scheduled travel date when available.
                            String travelDate = schedule.path("departure_date")
                                    .asText("");

                            if (travelDate.isBlank()) {
                                travelDate = joinedAt;
                            }

                            // Use trip_id if the joined trip object is missing.
                            long tripId = trip.path("id").asLong(
                                    node.path("trip_id").asLong(0));

                            String queuePos;

                            if (WaitingList.STATUS_WAITING.equalsIgnoreCase(status)) {

                                if (tripId > 0) {

                                    int position = waitingListService.getPosition(
                                            tripId,
                                            currentUser.getUserId());

                                    if (position > 0) {
                                        queuePos = String.valueOf(position);
                                    } else {
                                        queuePos = "—";
                                    }

                                } else {
                                    queuePos = "—";
                                }

                            } else if (WaitingList.STATUS_CONFIRMED
                                    .equalsIgnoreCase(status)) {

                                queuePos = "Promoted";

                            } else {

                                queuePos = "—";
                            }

                            String statusLabel;

                            if (WaitingList.STATUS_CONFIRMED
                                    .equalsIgnoreCase(status)) {

                                statusLabel = "Promoted";

                            } else {

                                statusLabel = status;
                            }

                            fetchedItems.add(new WaitlistItem(
                                    "WAIT-" + id,
                                    routeText,
                                    travelDate,
                                    departureTime,
                                    statusLabel,
                                    queuePos
                            ));
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to process waiting list entry: "
                                            + e.getMessage(),
                                    e);
                        }
                    }
                }

                return fetchedItems;
            }
        };

        task.setOnSucceeded(event -> {

            waitlistList.setAll(task.getValue());

        });

        task.setOnFailed(event -> {

            LOGGER.log(
                    Level.SEVERE,
                    "Unable to load passenger waiting list",
                    task.getException()
            );

        });

        Thread thread = new Thread(task, "anilink-waitlist-loader");

        thread.setDaemon(true);
        thread.start();
    }

    // Navigation Action Handlers

    @FXML
    private void handleHome(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/PassengerDashboard.fxml",
                1000,
                650
        );
    }

    @FXML
    private void handleReservations(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/Reservation.fxml",
                1000,
                800
        );
    }

    @FXML
    private void handleTripHistory(ActionEvent event) {

        viewNavigator.navigateTo(
                event,
                "/fxml/MyReservations.fxml",
                1000,
                650
        );
    }

    @FXML
    private void handleWaitingList(ActionEvent event) {

        // Already on the Waiting List page.
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

    // Inner Model for Table Binding

    public static class WaitlistItem {

        private final String waitlistCode;
        private final String route;
        private final String date;
        private final String departureTime;
        private final String status;
        private final String queuePos;

        public WaitlistItem(String waitlistCode,
                            String route,
                            String date,
                            String departureTime,
                            String status,
                            String queuePos) {

            this.waitlistCode = waitlistCode;
            this.route = route;
            this.date = date;
            this.departureTime = departureTime;
            this.status = status;
            this.queuePos = queuePos;
        }

        public String getWaitlistCode() {
            return waitlistCode;
        }

        public String getRoute() {
            return route;
        }

        public String getDate() {
            return date;
        }

        public String getDepartureTime() {
            return departureTime;
        }

        public String getStatus() {
            return status;
        }

        public String getQueuePos() {
            return queuePos;
        }
    }
}
