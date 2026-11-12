package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.Route;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.TripFullException;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.BookingValidationService;
import ph.edu.dlsu.anilink.service.QRService;
import ph.edu.dlsu.anilink.service.ReservationService;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.awt.image.BufferedImage;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JavaFX controller for the passenger reservation booking view.
 * <p>
 * This class manages the user interface where passengers can browse available routes,
 * select travel dates, and choose specific trip schedules. It relies on {@link SupabaseService}
 * to asynchronously fetch real-time routing and schedule data. Upon reservation submission,
 * it validates the booking request via {@link BookingValidationService}, posts the reservation
 * to the database, updates seat availability, and generates a unique QR code boarding pass
 * using {@link QRService}. It also handles navigation to other passenger-centric
 * views using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class ReservationController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final BookingValidationService bookingValidationService;
    private final ReservationService reservationService;
    private final QRService qrService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label userNameLabel;
    @FXML private ComboBox<String> routeSelection;
    @FXML private DatePicker dateSelection;
    @FXML private ComboBox<String> scheduleSelection;
    @FXML private Label availableSeats;
    @FXML private Label tripStatus;
    @FXML private Button reserveButton;
    @FXML private Label reservationMessage;

    private final Map<String, Route> routeMap = new HashMap<>();
    private final Map<String, TripDetails> tripMap = new HashMap<>();

    private Long selectedTripId = null;
    private int currentCapacity = 0;
    private int currentSeatsTaken = 0;

    public ReservationController(SupabaseService supabaseService,
                                 UserSession userSession,
                                 ViewNavigator viewNavigator,
                                 BookingValidationService bookingValidationService,
                                 ReservationService reservationService,
                                 QRService qrService) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.bookingValidationService = bookingValidationService;
        this.reservationService = reservationService;
        this.qrService = qrService;
    }

    @FXML
    private void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null && userNameLabel != null) {
            userNameLabel.setText("Welcome, " + user.getName());
        }

        setupListeners();
        loadRoutesAsync();
    }

    private void setupListeners() {
        // When route changes, reset date and schedule
        routeSelection.valueProperty().addListener((obs, oldVal, newVal) -> {
            dateSelection.setValue(null);
            scheduleSelection.getItems().clear();
            resetTripDetails();
        });

        // When date changes, load available trips if route is selected
        dateSelection.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && routeSelection.getValue() != null) {
                Route selectedRoute = routeMap.get(routeSelection.getValue());
                if (selectedRoute != null) {
                    loadTripsAsync(selectedRoute.getRouteId(), newVal);
                }
            }
        });

        // When schedule changes, update UI with selected trip capacity
        scheduleSelection.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && tripMap.containsKey(newVal)) {
                TripDetails details = tripMap.get(newVal);
                selectedTripId = details.tripId;
                currentCapacity = details.capacity;
                currentSeatsTaken = details.seatsTaken;

                availableSeats.setText("Available Seats: " + Math.max(0, currentCapacity - currentSeatsTaken));
                tripStatus.setText("Status: " + details.status);
            } else {
                resetTripDetails();
            }
        });
    }

    private void resetTripDetails() {
        selectedTripId = null;
        currentCapacity = 0;
        currentSeatsTaken = 0;
        availableSeats.setText("Available Seats: --");
        tripStatus.setText("Status: --");
        showMessage("", "#334155");
    }

    private void loadRoutesAsync() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String json = supabaseService.getRoutes();
                JsonNode routesArray = objectMapper.readTree(json);

                Platform.runLater(() -> {
                    routeSelection.getItems().clear();
                    routeMap.clear();

                    if (routesArray.isArray()) {
                        for (JsonNode node : routesArray) {
                            Route route = objectMapper.convertValue(node, Route.class);
                            String display = route.getOrigin() + " ↔ " + route.getDestination();

                            routeMap.put(display, route);
                            routeSelection.getItems().add(display);
                        }
                    }
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    private void loadTripsAsync(Long routeId, LocalDate date) {
        scheduleSelection.getItems().clear();
        tripMap.clear();
        resetTripDetails();
        showMessage("Loading schedules...", "#0284C7");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String json = supabaseService.getTripsByRoute(routeId);
                JsonNode tripsArray = objectMapper.readTree(json);

                Platform.runLater(() -> {
                    if (tripsArray.isArray() && !tripsArray.isEmpty()) {
                        for (JsonNode trip : tripsArray) {
                            long id = trip.path("id").asLong();
                            String time = trip.path("schedule").path("departure_time").asText("Unknown Time");
                            int capacity = trip.path("capacity").asInt(0);
                            int seatsTaken = trip.path("seats_taken").asInt(0);
                            String status = trip.path("status").asText("SCHEDULED");

                            String display = "Departure: " + time;
                            tripMap.put(display, new TripDetails(id, capacity, seatsTaken, status));
                            scheduleSelection.getItems().add(display);
                        }
                        showMessage("", "#334155");
                    } else {
                        showMessage("No schedules found for selected route and date.", "#DC2626");
                    }
                });
                return null;
            }
        };

        task.setOnFailed(e -> {
            Platform.runLater(() -> showMessage("Failed to load schedules.", "#DC2626"));
        });

        new Thread(task).start();
    }

    @FXML
    private void handleReserve() {
        User currentUser = userSession.getCurrentUser();
        LocalDate date = dateSelection.getValue();
        Route selectedRoute = routeMap.get(routeSelection.getValue());

        if (date == null || date.isBefore(LocalDate.now())) {
            showMessage("Please select a valid future date.", "#DC2626");
            return;
        }

        if (selectedTripId == null || selectedRoute == null) {
            showMessage("Please select a valid route and schedule.", "#DC2626");
            return;
        }

        // Construct Trip object WITH Route attached
        Trip currentTrip = new Trip();
        currentTrip.setTripId(selectedTripId);
        currentTrip.setRoute(selectedRoute); // <--- THIS FIXES THE VALIDATION ERROR
        currentTrip.setCapacity(currentCapacity);
        currentTrip.setSeatsTaken(currentSeatsTaken);

        reserveButton.setDisable(true);
        showMessage("Processing reservation...", "#0284C7");

        // Execute Reservation asynchronously
        Task<Reservation> task = new Task<>() {
            @Override
            protected Reservation call() throws Exception {
                return reservationService.createReservation(currentUser, currentTrip);
            }
        };

        task.setOnSucceeded(e -> {
            reserveButton.setDisable(false);
            Reservation reservation = task.getValue();
            availableSeats.setText("Available Seats: " + Math.max(0, currentCapacity - currentSeatsTaken - 1));
            showMessage("Reservation successful!", "#16A34A");

            // Generate QR Code image from the reservation payload
            BufferedImage bufferedImage = qrService.generateQRCodeImage(reservation.getQrPayload(), 250, 250);
            Image qrImage = bufferedImage != null ? SwingFXUtils.toFXImage(bufferedImage, null) : null;

            // Pop up the generated QR code dialog
            showQRCodePopup(qrImage, reservation.getQrPayload());

            // Clear selected schedule
            scheduleSelection.setValue(null);
        });

        task.setOnFailed(e -> {
            reserveButton.setDisable(false);
            Throwable ex = task.getException();
            if (ex instanceof TripFullException) {
                showJoinWaitlistDialog(currentTrip);
            } else {
                if (ex != null) {
                    ex.printStackTrace();
                }
                showMessage("Failed to process reservation in Supabase.", "#DC2626");
            }
        });

        new Thread(task).start();
    }

    /**
     * Shows a confirmation dialog offering to join the waiting list when a trip is full.
     */
    private void showJoinWaitlistDialog(Trip trip) {
        User currentUser = userSession.getCurrentUser();
        if (currentUser == null) {
            showMessage("Please log in to manage the waiting list.", "#DC2626");
            return;
        }

        int existingPosition = reservationService.getWaitingListPosition(currentUser, trip);

        if (existingPosition != -1) {
            // Already on the waiting list — offer to cancel
            Alert cancelAlert = new Alert(Alert.AlertType.CONFIRMATION);
            cancelAlert.setTitle("Cancel Waiting List");
            cancelAlert.setHeaderText("You are #" + existingPosition + " on the waiting list.");
            cancelAlert.setContentText("Would you like to remove yourself from the waiting list for this trip?");

            Optional<ButtonType> cancelResult = cancelAlert.showAndWait();
            if (cancelResult.isPresent() && cancelResult.get() == ButtonType.OK) {
                Task<Boolean> cancelTask = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return reservationService.cancelWaitlist(currentUser, trip);
                    }
                };
                cancelTask.setOnSucceeded(e -> {
                    if (cancelTask.getValue()) {
                        showMessage("You have been removed from the waiting list.", "#16A34A");
                    } else {
                        showMessage("Failed to cancel waiting list entry.", "#DC2626");
                    }
                });
                cancelTask.setOnFailed(e -> {
                    showMessage("Failed to cancel waiting list entry.", "#DC2626");
                });
                new Thread(cancelTask).start();
            }
            return;
        }

        // Not on the waiting list — offer to join
        Alert joinAlert = new Alert(Alert.AlertType.CONFIRMATION);
        joinAlert.setTitle("Trip Fully Booked");
        joinAlert.setHeaderText("This trip is fully booked.");
        joinAlert.setContentText("Would you like to join the waiting list? You'll be notified if a seat opens up.");

        Optional<ButtonType> joinResult = joinAlert.showAndWait();
        if (joinResult.isPresent() && joinResult.get() == ButtonType.OK) {
            Task<Boolean> waitlistTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    return reservationService.joinWaitingList(currentUser, trip);
                }
            };
            waitlistTask.setOnSucceeded(e -> {
                if (waitlistTask.getValue()) {
                    int newPosition = reservationService.getWaitingListPosition(currentUser, trip);
                    showMessage("You are #" + newPosition + " on the waiting list.", "#0284C7");
                } else {
                    showMessage("Failed to join waiting list.", "#DC2626");
                }
            });
            waitlistTask.setOnFailed(e -> {
                showMessage("Failed to join waiting list.", "#DC2626");
            });
            new Thread(waitlistTask).start();
        }
    }

    private void showQRCodePopup(Image qrImage, String payload) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Reservation Confirmed");
            alert.setHeaderText("Your Boarding Pass QR Code");
            alert.setContentText("Present this QR code to the driver upon boarding.\n\nTicket Code: " + payload);

            if (qrImage != null) {
                ImageView imageView = new ImageView(qrImage);
                imageView.setFitWidth(250);
                imageView.setFitHeight(250);
                alert.setGraphic(imageView);
            }

            alert.showAndWait();
        });
    }

    private void showMessage(String message, String colorCode) {
        reservationMessage.setText(message);
        reservationMessage.setStyle("-fx-text-fill: " + colorCode + "; -fx-font-weight: bold; -fx-font-size: 13px;");
    }

    // Sidebar Action Handlers
    @FXML
    private void handleHome(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/PassengerDashboard.fxml", 1000, 650);
    }

    @FXML
    private void handleReservations(ActionEvent event) {
        // Already here
    }

    @FXML
    private void handleTripHistory(ActionEvent event) {
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

    // Helper record/class for trip data mapping
    private static class TripDetails {
        final Long tripId;
        final int capacity;
        final int seatsTaken;
        final String status;

        TripDetails(Long tripId, int capacity, int seatsTaken, String status) {
            this.tripId = tripId;
            this.capacity = capacity;
            this.seatsTaken = seatsTaken;
            this.status = status;
        }
    }
}