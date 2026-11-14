package ph.edu.dlsu.anilink.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.Reservation;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.BookingSummaryService;
import ph.edu.dlsu.anilink.service.QRService;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * JavaFX controller for displaying reservation details and their corresponding QR codes.
 * <p>
 * This class handles the user interface for a passenger's reservation confirmation.
 * It receives a specific {@link Reservation} object, updates the UI with the trip
 * and passenger details, and asynchronously generates a QR code image using
 * {@link QRService} to prevent blocking the JavaFX Application Thread. It also
 * provides navigation handlers to return to the dashboard or access other passenger
 * modules using the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class QRCodeController {

    private final QRService qrService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final BookingSummaryService bookingSummaryService;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @FXML private Label userNameLabel;
    @FXML private ImageView qrCode;
    @FXML private Label reservationCode;
    @FXML private Label passengerName;
    @FXML private Label route;
    @FXML private Label departureTime;
    @FXML private Label status;
    @FXML private Button back;
    @FXML private Button downloadQR;
    @FXML private Button downloadSummary;

    private Reservation reservation;

    public QRCodeController(QRService qrService,
                            UserSession userSession,
                            ViewNavigator viewNavigator,
                            BookingSummaryService bookingSummaryService,
                            SupabaseService supabaseService) {
        this.qrService = qrService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
        this.bookingSummaryService = bookingSummaryService;
        this.supabaseService = supabaseService;
    }

    @FXML
    private void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null && userNameLabel != null) {
            userNameLabel.setText("Welcome, " + user.getName());
        }
    }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;

        if (reservation == null) {
            status.setText("No reservation selected");
            return;
        }

        reservationCode.setText("Reservation Code: " + (reservation.getReservationId() != null ? reservation.getReservationId() : "N/A"));

        if (reservation.getPassenger() != null) {
            passengerName.setText("Passenger: " + reservation.getPassenger().getName());
        } else {
            passengerName.setText("Passenger: Unknown");
        }

        if (reservation.getTrip() != null && reservation.getTrip().getRoute() != null) {
            String origin = reservation.getTrip().getRoute().getOrigin();
            String destination = reservation.getTrip().getRoute().getDestination();
            route.setText("Route: " + origin + " ↔ " + destination);
        } else {
            route.setText("Route: N/A");
        }

        if (reservation.getTrip() != null && reservation.getTrip().getSchedule() != null) {
            departureTime.setText("Departure: " + reservation.getTrip().getSchedule().getDepartureTime());
        } else {
            departureTime.setText("Departure: N/A");
        }

        status.setText(reservation.getStatus() != null ? reservation.getStatus() : "WAITLISTED");

        generateQRCodeAsync();
    }

    private void generateQRCodeAsync() {
        if (reservation == null) {
            status.setText("No reservation selected");
            return;
        }

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                BufferedImage bufferedImage = qrService.generateQRCode(reservation);
                if (bufferedImage != null) {
                    return SwingFXUtils.toFXImage(bufferedImage, null);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            Image image = task.getValue();
            if (image != null) {
                qrCode.setImage(image);
            } else {
                status.setText("QR generation failed");
            }
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            status.setText("Error rendering QR Code");
        });

        new Thread(task).start();
    }

    /**
     * Downloads the displayed QR code image as a PNG file.
     */
    @FXML
    private void downloadQRCode(ActionEvent event) {
        if (qrCode.getImage() == null) {
            showAlert("No QR Code", "There is no QR code to download.");
            return;
        }

        Stage ownerStage = (Stage) qrCode.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save QR Code");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName("AniLink_QR_" +
                (reservation != null && reservation.getReservationId() != null
                        ? reservation.getReservationId() : "reservation") + ".png");

        File file = fileChooser.showSaveDialog(ownerStage);
        if (file != null) {
            try {
                BufferedImage bImage = SwingFXUtils.fromFXImage(qrCode.getImage(), null);
                ImageIO.write(bImage, "png", file);
                showAlert("QR Code Saved", "QR code saved to:\n" + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Save Failed", "Failed to save QR code: " + e.getMessage());
            }
        }
    }

    /**
     * Downloads a booking confirmation summary text file for the current reservation.
     * Fetches full reservation details from Supabase to ensure accuracy.
     */
    @FXML
    private void downloadSummary(ActionEvent event) {
        if (reservation == null || reservation.getReservationId() == null) {
            showAlert("No Reservation", "There is no reservation to summarize.");
            return;
        }

        Task<Reservation> task = new Task<>() {
            @Override
            protected Reservation call() throws Exception {
                String json = supabaseService.getReservationFullDetails(reservation.getReservationId());
                if (json == null || json.isBlank()) {
                    throw new RuntimeException("No reservation data returned from Supabase.");
                }

                com.fasterxml.jackson.databind.JsonNode root =
                        objectMapper.readTree(json);
                if (!root.isArray() || root.isEmpty()) {
                    throw new RuntimeException("Reservation not found in Supabase.");
                }

                com.fasterxml.jackson.databind.JsonNode node = root.get(0);
                return objectMapper.treeToValue(node, Reservation.class);
            }
        };

        task.setOnSucceeded(e -> {
            Reservation fullReservation = task.getValue();
            String summary = bookingSummaryService.generateSummary(fullReservation);

            Stage ownerStage = (Stage) qrCode.getScene().getWindow();
            bookingSummaryService.saveToFile(summary, ownerStage);

            showAlert("Download Ready",
                    "Booking summary for RES-" + fullReservation.getReservationId() +
                            " is ready to save. Please choose a location.");
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

    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Sidebar Action Handlers
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
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    @FXML
    private void handleBack(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/PassengerDashboard.fxml", 1000, 650);
    }
}