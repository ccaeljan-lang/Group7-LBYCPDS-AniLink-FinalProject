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
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.service.WaitingListService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX controller for the passenger's Waiting List view.
 * <p>
 * This class manages the interface where a passenger can view their pending reservations
 * and waiting list (queue) positions. It retrieves the logged-in user from the {@link UserSession},
 * asynchronously fetches their specific waiting list data via {@link SupabaseService},
 * and populates a JavaFX {@link TableView} with the results. It also provides
 * navigation functions to return to the dashboard, book new reservations,
 * view active reservations, or log out using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class WaitingListController {

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

    private final ObservableList<WaitlistItem> waitlistList = FXCollections.observableArrayList();

    public WaitingListController(SupabaseService supabaseService,
                                 UserSession userSession,
                                 ViewNavigator viewNavigator, WaitingListService waitingListService) {
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
        waitlistCodeColumn.setCellValueFactory(new PropertyValueFactory<>("waitlistCode"));
        routeColumn.setCellValueFactory(new PropertyValueFactory<>("route"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        departureColumn.setCellValueFactory(new PropertyValueFactory<>("departureTime"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        queuePosColumn.setCellValueFactory(new PropertyValueFactory<>("queuePos"));

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
                String json = supabaseService.getWaitingListByPassenger(currentUser.getUserId());
                JsonNode array = objectMapper.readTree(json);

                if (array.isArray()) {
                    for (JsonNode node : array) {
                        long id = node.path("id").asLong();
                        String status = node.path("status").asText("N/A");
                        String joinedAt = node.path("joined_at").asText("N/A").split("T")[0];

                        JsonNode trip = node.path("trip");
                        JsonNode route = trip.path("route");
                        JsonNode schedule = trip.path("schedule");

                        String origin = route.path("origin").asText("Unknown");
                        String destination = route.path("destination").asText("Unknown");
                        String routeText = origin + " ↔ " + destination;
                        String departureTime = schedule.path("departure_time").asText("N/A");

                        Long tripId = trip.path("id").asLong();
                        int queuePosition = waitingListService.getPosition(tripId, currentUser.getUserId());

                        fetchedItems.add(new WaitlistItem(
                                "WAIT-" + id,
                                routeText,
                                joinedAt,
                                departureTime,
                                status,
                                queuePosition
                        ));
                    }
                }
                return fetchedItems;
            }
        };

        task.setOnSucceeded(e -> {
            waitlistList.clear();
            waitlistList.addAll(task.getValue());
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        new Thread(task).start();
    }

    // Navigation Action Handlers
    @FXML
    private void handleHome(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/PassengerDashboard.fxml", 1000, 650);
    }

    @FXML
    private void handleReservations(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/Reservation.fxml", 1000, 650);
    }

    @FXML
    private void handleTripHistory(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/MyReservations.fxml", 1000, 650);
    }

    @FXML
    private void handleWaitingList(ActionEvent event) {
        // Already on Waiting List view
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    // Inner Model for Table Binding
    public static class WaitlistItem {
        private final String waitlistCode;
        private final String route;
        private final String date;
        private final String departureTime;
        private final String status;
        private final int queuePos;

        public WaitlistItem(String reservationCode, String route, String date, String departureTime, String status, int queuePos) {
            this.waitlistCode = reservationCode;
            this.route = route;
            this.date = date;
            this.departureTime = departureTime;
            this.status = status;
            this.queuePos = queuePos;
        }

        public String getWaitlistCode() { return waitlistCode; }
        public String getRoute() { return route; }
        public String getDate() { return date; }
        public String getDepartureTime() { return departureTime; }
        public String getStatus() { return status; }
        public int getQueuePos() { return queuePos; }
    }
}