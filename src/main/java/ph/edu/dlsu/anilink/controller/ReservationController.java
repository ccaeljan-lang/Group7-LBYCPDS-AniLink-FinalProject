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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.Route;
import ph.edu.dlsu.anilink.model.Trip;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.BookingValidationService;
import ph.edu.dlsu.anilink.service.QRService;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.awt.image.BufferedImage;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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
                                 QRService qrService) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.bookingValidationService = bookingValidationService;
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

        BookingValidationService.ValidationResult result =
                bookingValidationService.validateBooking(currentUser, currentTrip);

        if (!result.isValid()) {
            showMessage(result.getMessage(), "#DC2626");
            return;
        }

        reserveButton.setDisable(true);
        showMessage("Processing reservation...", "#0284C7");

        // Unique payload matching your `qr_payload` column constraint
        String qrPayload = "ANILINK-RES-" + UUID.randomUUID().toString();

        // Execute Reservation asynchronously
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                // 1. Post to Supabase reservations table
                supabaseService.postReservation(currentUser.getUserId(), selectedTripId, qrPayload);

                // 2. Increment seats taken
                int updatedSeats = currentSeatsTaken + 1;
                supabaseService.updateTripSeatsTaken(selectedTripId, updatedSeats);
                currentSeatsTaken = updatedSeats;

                // 3. Generate QR Code image
                BufferedImage bufferedImage = qrService.generateQRCodeImage(qrPayload, 250, 250);
                return bufferedImage != null ? SwingFXUtils.toFXImage(bufferedImage, null) : null;
            }
        };

        task.setOnSucceeded(e -> {
            reserveButton.setDisable(false);
            availableSeats.setText("Available Seats: " + Math.max(0, currentCapacity - currentSeatsTaken));
            showMessage("Reservation successful!", "#16A34A");

            // Pop up the generated QR code dialog
            Image qrImage = task.getValue();
            showQRCodePopup(qrImage, qrPayload);

            // Clear selected schedule
            scheduleSelection.setValue(null);
        });

        task.setOnFailed(e -> {
            reserveButton.setDisable(false);
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            showMessage("Failed to process reservation in Supabase.", "#DC2626");
        });

        new Thread(task).start();
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