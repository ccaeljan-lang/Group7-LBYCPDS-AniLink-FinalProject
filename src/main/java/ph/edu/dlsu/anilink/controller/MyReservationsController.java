package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.BookingSummaryService;
import ph.edu.dlsu.anilink.service.ReservationService;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import ph.edu.dlsu.anilink.dsa.TripInsertionSort;

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
    private final ReservationService reservationService;
    private final BookingSummaryService bookingSummaryService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @FXML private Label passengerNameLabel;
    @FXML private TableView<ReservationItem> reservationTable;
    @FXML private TableColumn<ReservationItem, String> reservationCodeColumn;
    @FXML private TableColumn<ReservationItem, String> routeColumn;
    @FXML private TableColumn<ReservationItem, String> dateColumn;
    @FXML private TableColumn<ReservationItem, String> departureColumn;
    @FXML private TableColumn<ReservationItem, String> statusColumn;
    @FXML private TableColumn<ReservationItem, String> downloadColumn;
    @FXML private TableColumn<ReservationItem, String> cancelColumn;
    @FXML private TableColumn<ReservationItem, String> tripNumberColumn;

    private final ObservableList<ReservationItem> reservationList = FXCollections.observableArrayList();

    public MyReservationsController(SupabaseService supabaseService,
                                    UserSession userSession,
                                    ViewNavigator viewNavigator,
                                    ReservationService reservationService,
                                    BookingSummaryService bookingSummaryService) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.reservationService = reservationService;
        this.bookingSummaryService = bookingSummaryService;
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

        // Download column with a "Download Summary" button for each reservation row
        // Hidden for cancelled or completed reservations
        downloadColumn.setCellFactory(param -> new TableCell<>() {
            private final Button downloadButton = new Button("Download");

            {
                downloadButton.setOnAction(event -> {
                    ReservationItem item = getTableView().getItems().get(getIndex());
                    handleDownloadSummary(item);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReservationItem ri = getTableView().getItems().get(getIndex());
                    String status = ri.getStatus() != null ? ri.getStatus() : "";
                    boolean isActive = !"CANCELLED".equalsIgnoreCase(status)
                            && !"COMPLETED".equalsIgnoreCase(status);
                    setGraphic(isActive ? downloadButton : null);
                }
            }
        });

        // Cancel column with a "Cancel" button for each reservation row
        // Hidden for cancelled or completed reservations
        cancelColumn.setCellFactory(param -> new TableCell<>() {
            private final Button cancelButton = new Button("Cancel");

            {
                cancelButton.setOnAction(event -> {
                    ReservationItem item = getTableView().getItems().get(getIndex());
                    handleCancelReservation(item);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReservationItem ri = getTableView().getItems().get(getIndex());
                    String status = ri.getStatus() != null ? ri.getStatus() : "";
                    boolean isActive = !"CANCELLED".equalsIgnoreCase(status)
                            && !"COMPLETED".equalsIgnoreCase(status);
                    setGraphic(isActive ? cancelButton : null);
                }
            }
        });

        // Trip Number column: shows the actual trip id from the trips table.
        tripNumberColumn.setCellValueFactory(new PropertyValueFactory<>("tripNumber"));

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
                // 2. Load this passenger's reservations (nested trip/route/schedule).
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

                        Long tripId = trip.path("id").asLong();
                        Long scheduleId = schedule.path("id").asLong();

                        String origin = route.path("origin").asText("Unknown");
                        String destination = route.path("destination").asText("Unknown");
                        String routeText = origin + " ↔ " + destination;
                        String departureTime = schedule.path("departure_time").asText("N/A");

                        // Travel date from the trip's schedule; fall back to booking date
                        // for legacy records with no departure date.
                        String departureDate = schedule.path("departure_date").asText(null);
                        String travelDate = (departureDate != null && !departureDate.isBlank())
                                ? departureDate : createdAt;

                        // Use the actual trip id from the trips table as the user-facing
                        // trip number (matches the dashboard), not a computed ordinal.
                        int tripNumber = (tripId != null && tripId > 0) ? tripId.intValue() : -1;

                        fetchedItems.add(new ReservationItem(
                                id,
                                "RES-" + id,
                                routeText,
                                travelDate,
                                departureTime,
                                status,
                                tripId,
                                scheduleId,
                                tripNumber
                        ));
                    }
                }

                // 3. Stable Insertion Sort by numeric trip number (DSA requirement).
                //    #2 must precede #10; equal trip numbers keep their relative order.
                TripInsertionSort.sortByTripNumber(fetchedItems);
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
        viewNavigator.navigateTo(event, "/fxml/Reservation.fxml", 1000, 800);
    }

    @FXML
    private void handleTripHistory(ActionEvent event) {
        // Already on Trip History view
    }

    @FXML
    private void handleWaitingList(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/WaitingList.fxml", 1000, 650);
    }

    /**
     * Downloads a booking confirmation summary for the selected reservation.
     * Fetches full reservation details from Supabase and generates a formatted
     * text file that the passenger can save locally.
     */
    @FXML
    private void handleDownloadSummary(ReservationItem item) {
        if (item.getReservationId() == null) {
            showAlert("Missing Reservation ID", "Unable to download summary: reservation ID is missing.");
            return;
        }

        String status = item.getStatus() != null ? item.getStatus() : "";
        if ("CANCELLED".equalsIgnoreCase(status)) {
            showAlert("Cancelled Reservation", "Cannot download summary for a cancelled reservation.");
            return;
        }
        if ("COMPLETED".equalsIgnoreCase(status)) {
            showAlert("Completed Reservation", "Cannot download summary for a completed reservation.");
            return;
        }

        Task<Reservation> task = new Task<>() {
            @Override
            protected Reservation call() throws Exception {
                String json = supabaseService.getReservationFullDetails(item.getReservationId());
                if (json == null || json.isBlank()) {
                    throw new RuntimeException("No reservation data returned from Supabase.");
                }

                com.fasterxml.jackson.databind.JsonNode root =
                        objectMapper.readTree(json);
                if (!root.isArray() || root.isEmpty()) {
                    throw new RuntimeException("Reservation not found in Supabase.");
                }

                com.fasterxml.jackson.databind.JsonNode node = root.get(0);
                Reservation reservation = objectMapper.treeToValue(node, Reservation.class);
                return reservation;
            }
        };

        task.setOnSucceeded(e -> {
            Reservation reservation = task.getValue();
            BufferedImage summaryImage = bookingSummaryService.generateSummaryImage(reservation);

            javafx.stage.Stage ownerStage =
                    (javafx.stage.Stage) reservationTable.getScene().getWindow();
            boolean saved = bookingSummaryService.saveToFile(summaryImage, ownerStage);

            if (saved) {
                showAlert("Booking Summary Saved",
                        "Your booking summary and proof (RES-" + reservation.getReservationId() +
                                ") has been saved successfully. You can view and download it again from My Reservations.");
            } else {
                // File dialog cancelled or write failed: do not announce success.
                showAlert("Booking Summary Not Saved",
                        "The booking summary was not saved. You may try downloading again from My Reservations.");
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) {
                ex.printStackTrace();
            }
            showAlert("Download Failed",
                    "Failed to download booking summary: " +
                            (ex != null ? ex.getMessage() : "Unknown error"));
        });

        new Thread(task).start();
    }

    /**
     * Cancels the selected reservation from the passenger's standpoint.
     * Marks it as CANCELLED, frees up the seat, and promotes the next
     * waiting passenger if one exists.
     */
    @FXML
    private void handleCancelReservation(ReservationItem item) {
        if (item.getReservationId() == null) {
            showAlert("Missing Reservation ID", "Unable to cancel: reservation ID is missing.");
            return;
        }

        String status = item.getStatus() != null ? item.getStatus() : "";
        if ("CANCELLED".equalsIgnoreCase(status)) {
            showAlert("Already Cancelled", "This reservation is already cancelled.");
            return;
        }
        if ("COMPLETED".equalsIgnoreCase(status)) {
            showAlert("Trip Completed", "This reservation has already been completed and cannot be cancelled.");
            return;
        }

        // Confirm before cancelling
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Reservation");
        confirm.setHeaderText("Cancel Reservation " + item.getReservationCode() + "?");
        confirm.setContentText("This will free up your seat on the trip. The next waiting passenger may be automatically assigned.");

        java.util.Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Reservation> task = new Task<>() {
                @Override
                protected Reservation call() throws Exception {
                    return reservationService.cancelReservation(item.getReservationId());
                }
            };

            task.setOnSucceeded(e -> {
                Reservation cancelled = task.getValue();
                showAlert("Reservation Cancelled",
                        "Your reservation " + cancelled.getReservationId() +
                                " has been cancelled successfully. Your seat has been released.");
                loadReservationsAsync();
            });

            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                if (ex != null) {
                    ex.printStackTrace();
                }
                showAlert("Cancel Failed",
                        "Failed to cancel reservation: " +
                                (ex != null ? ex.getMessage() : "Unknown error"));
            });

            new Thread(task).start();
        }
    }

    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    // Inner Model for Table Binding
    public static class ReservationItem {
        private final Long reservationId;
        private final String reservationCode;
        private final String route;
        // date holds the TRAVEL date (trip.schedule.departure_date, fallback to
        // booking created_at for legacy records), not the database id.
        private final String date;
        private final String departureTime;
        private final String status;
        private final Long tripId;
        private final Long scheduleId;
        // tripNumber is the user-facing trip number from the trips table,
        // matching what is shown on the dashboard. -1 means unknown.
        private final int tripNumber;

        public ReservationItem(Long reservationId, String reservationCode, String route, String date,
                               String departureTime, String status, Long tripId, Long scheduleId,
                               int tripNumber) {
            this.reservationId = reservationId;
            this.reservationCode = reservationCode;
            this.route = route;
            this.date = date;
            this.departureTime = departureTime;
            this.status = status;
            this.tripId = tripId;
            this.scheduleId = scheduleId;
            this.tripNumber = tripNumber;
        }

        public Long getReservationId() { return reservationId; }
        public String getReservationCode() { return reservationCode; }
        public String getRoute() { return route; }
        public String getDate() { return date; }
        // travelDate alias for the DSA sort comparator; same value as getDate().
        public String getTravelDate() { return date; }
        public String getDepartureTime() { return departureTime; }
        public String getStatus() { return status; }
        public Long getTripId() { return tripId; }
        public Long getScheduleId() { return scheduleId; }
        public int getTripNumber() { return tripNumber; }
    }
}