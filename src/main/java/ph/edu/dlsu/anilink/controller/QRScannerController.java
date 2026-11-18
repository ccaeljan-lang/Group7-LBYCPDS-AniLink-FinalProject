package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.springframework.stereotype.Controller;
import ph.edu.dlsu.anilink.model.User;
import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

import java.util.Optional;

/**
 * JavaFX controller for the QR Scanner view.
 * <p>
 * This class handles the interface used by drivers to verify passenger reservations
 * prior to boarding. It provides functionality to simulate scanning a QR code payload
 * and asynchronously verifies the data against the database using {@link SupabaseService}.
 * Based on the verification results, it updates the passenger's reservation status
 * to "BOARDED", prevents duplicate boarding, and safely displays visual feedback and
 * alerts on the JavaFX Application Thread. It also includes handlers for navigating
 * to other driver-centric modules via the {@link ViewNavigator}.
 * </p>
 */
@Controller
public class QRScannerController {

    private final SupabaseService supabaseService;
    private final UserSession userSession;
    private final ViewNavigator viewNavigator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label driverNameLabel;
    @FXML private Label scanResultLabel;
    @FXML private Button simulateScanButton;
    @FXML private Button backButton;

    public QRScannerController(SupabaseService supabaseService,
                               UserSession userSession,
                               ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;
    }

    @FXML
    private void initialize() {
        User user = userSession.getCurrentUser();
        if (user != null && driverNameLabel != null) {
            driverNameLabel.setText("Welcome, " + user.getName());
        }
        scanResultLabel.setText("Awaiting scan...");
    }

    @FXML
    private void handleScanQR() {
        Stage ownerStage = (Stage) scanResultLabel.getScene().getWindow();
        CameraScannerDialog scannerDialog = new CameraScannerDialog(ownerStage);
        Optional<String> result = scannerDialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            verifyAndBoardPassengerAsync(result.get().trim());
        }
    }

    private void verifyAndBoardPassengerAsync(String qrPayload) {
        scanResultLabel.setText("Verifying...");
        scanResultLabel.setStyle("-fx-text-fill: #0284C7; -fx-font-weight: bold; -fx-font-size: 14px;");

        Task<VerificationResult> task = new Task<>() {
            @Override
            protected VerificationResult call() throws Exception {
                String json = supabaseService.getReservationByQrPayload(qrPayload);
                JsonNode array = objectMapper.readTree(json);

                if (array.isArray() && !array.isEmpty()) {
                    JsonNode reservation = array.get(0);
                    long reservationId = reservation.path("id").asLong();
                    String currentStatus = reservation.path("status").asText("WAITLISTED");

                    JsonNode passenger = reservation.path("passenger");
                    String passengerName = passenger.path("name").asText("Passenger");

                    if ("BOARDED".equalsIgnoreCase(currentStatus)) {
                        return new VerificationResult(Alert.AlertType.WARNING,
                                "Already Boarded", passengerName + " has already been verified and boarded.",
                                "Already Scanned: " + passengerName, "#D97706");
                    } else if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                        return new VerificationResult(Alert.AlertType.ERROR,
                                "Cancelled Reservation", "This reservation was cancelled.",
                                "Invalid: Reservation Cancelled", "#DC2626");
                    } else {
                        supabaseService.updateReservationStatus(reservationId, "BOARDED");
                        return new VerificationResult(Alert.AlertType.INFORMATION,
                                "Boarding Approved", "Passenger " + passengerName + " successfully verified!",
                                "Verified: " + passengerName + " (BOARDED)", "#16A34A");
                    }
                } else {
                    return new VerificationResult(Alert.AlertType.ERROR,
                            "Verification Failed", "No active reservation matches this QR code.",
                            "Invalid: QR Code Not Found", "#DC2626");
                }
            }
        };

        task.setOnSucceeded(e -> {
            VerificationResult result = task.getValue();
            scanResultLabel.setText(result.labelText);
            scanResultLabel.setStyle("-fx-text-fill: " + result.colorCode + "; -fx-font-weight: bold; -fx-font-size: 14px;");
            showAlert(result.type, result.title, result.message);
        });

        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            scanResultLabel.setText("Error processing scan");
            scanResultLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 14px;");
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to communicate with Supabase.");
        });

        new Thread(task).start();
    }

    // Sidebar Action Handlers
    @FXML
    private void handleDashboard(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/DriverDashboard.fxml", 1000, 650);
    }

    @FXML
    private void handleTripDetails(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/TripDetails.fxml", 1000, 650);
    }

    @FXML
    private void handleScanQR(ActionEvent event) {
        // Already here
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", 900, 600);
    }

    @FXML
    private void handleBack(ActionEvent event) {
        viewNavigator.navigateTo(event, "/fxml/DriverDashboard.fxml", 1000, 650);
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

    // Helper class to pass results from background thread to UI thread
    private static class VerificationResult {
        final Alert.AlertType type;
        final String title;
        final String message;
        final String labelText;
        final String colorCode;

        VerificationResult(Alert.AlertType type, String title, String message, String labelText, String colorCode) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.labelText = labelText;
            this.colorCode = colorCode;
        }
    }
}