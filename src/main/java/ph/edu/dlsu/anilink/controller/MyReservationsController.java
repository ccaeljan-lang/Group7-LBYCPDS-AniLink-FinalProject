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
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX controller for the passenger's My Reservations view.
 * <p>
 * This class manages the interface where a passenger can view their current
 * and past reservations. It retrieves the logged-in user from the {@link UserSession},
 * asynchronously fetches their specific reservation data via {@link SupabaseService},
 * and populates a JavaFX {@link TableView} with the results. It also provides
 * navigation functions to return to the dashboard, book new reservations, or
 * log out using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class MyReservationsController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label passengerNameLabel;
    @FXML private TableView<ReservationItem> reservationTable;
    @FXML private TableColumn<ReservationItem, String> reservationCodeColumn;
    @FXML private TableColumn<ReservationItem, String> routeColumn;
    @FXML private TableColumn<ReservationItem, String> dateColumn;
    @FXML private TableColumn<ReservationItem, String> departureColumn;
    @FXML private TableColumn<ReservationItem, String> statusColumn;

    private final ObservableList<ReservationItem> reservationList = FXCollections.observableArrayList();

    public MyReservationsController(SupabaseService supabaseService,
                                    UserSession userSession,
                                    ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    @FXML
    private void initialize() {
        User currentUser = userSession.getCurrentUser();
        if (currentUser != null && passengerNameLabel != null) {
            passengerNameLabel.setText("Welcome, " + currentUser.getName());
        }

        setupTableColumns();
        loadReservationsAsync();
    }

    private void setupTableColumns() {
        reservationCodeColumn.setCellValueFactory(new PropertyValueFactory<>("reservationCode"));
        routeColumn.setCellValueFactory(new PropertyValueFactory<>("route"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        departureColumn.setCellValueFactory(new PropertyValueFactory<>("departureTime"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        reservationTable.setItems(reservationList);
    }

    private void loadReservationsAsync() {
        User currentUser = userSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        Task<List<ReservationItem>> task = new Task<>() {
            @Override
            protected List<ReservationItem> call() throws Exception {
                List<ReservationItem> fetchedItems = new ArrayList<>();
                String json = supabaseService.getReservationsByPassenger(currentUser.getUserId());
                JsonNode array = objectMapper.readTree(json);

                if (array.isArray()) {
                    for (JsonNode node : array) {
                        long id = node.path("id").asLong();
                        String status = node.path("status").asText("WAITLISTED");
                        String createdAt = node.path("created_at").asText("").split("T")[0];

                        JsonNode trip = node.path("trip");
                        JsonNode route = trip.path("route");
                        JsonNode schedule = trip.path("schedule");

                        String origin = route.path("origin").asText("Unknown");
                        String destination = route.path("destination").asText("Unknown");
                        String routeText = origin + " ↔ " + destination;
                        String departureTime = schedule.path("departure_time").asText("N/A");

                        fetchedItems.add(new ReservationItem(
                                "RES-" + id,
                                routeText,
                                createdAt,
                                departureTime,
                                status
                        ));
                    }
                }
                return fetchedItems;
            }
        };

        task.setOnSucceeded(e -> {
            reservationList.clear();
            reservationList.addAll(task.getValue());
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
        // Already on Trip History view
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

    // Inner Model for Table Binding
    public static class ReservationItem {
        private final String reservationCode;
        private final String route;
        private final String date;
        private final String departureTime;
        private final String status;

        public ReservationItem(String reservationCode, String route, String date, String departureTime, String status) {
            this.reservationCode = reservationCode;
            this.route = route;
            this.date = date;
            this.departureTime = departureTime;
            this.status = status;
        }

        public String getReservationCode() { return reservationCode; }
        public String getRoute() { return route; }
        public String getDate() { return date; }
        public String getDepartureTime() { return departureTime; }
        public String getStatus() { return status; }
    }
}