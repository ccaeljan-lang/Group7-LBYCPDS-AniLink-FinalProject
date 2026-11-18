package ph.edu.dlsu.anilink.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

/**
 * Camera scanner dialog for drivers to scan passenger QR codes during boarding.
 *
 * <p>Opens a modal window with a live laptop camera preview. The driver can
 * click "Capture &amp; Scan" to take a frame and decode the QR code using ZXing,
 * or upload an image file containing a QR code. When a valid QR code is detected,
 * the scanner returns the payload so the driver can verify and board the passenger.</p>
 */
public class CameraScannerDialog {

    private final Stage ownerStage;
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private Stage dialogStage;
    private Label cameraStatusLabel;
    private Button startStopButton;
    private Button cancelButton;
    private ImageView cameraFeed;

    private Webcam webcam;
    private volatile String scannedPayload;

    /**
     * Constructs the camera scanner dialog.
     *
     * @param ownerStage the parent stage for modality
     */
    public CameraScannerDialog(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    /**
     * Shows the camera scanner modal and waits for a scan result.
     *
     * @return the scanned QR payload, or {@link Optional#empty()} if cancelled
     */
    public Optional<String> showAndWait() {
        dialogStage = new Stage();
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Camera Scanner - Boarding Verification");
        dialogStage.setMinWidth(560);
        dialogStage.setMinHeight(600);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F8FAFC;");
        root.setPadding(new Insets(20));

        // Camera preview area
        VBox cameraContainer = new VBox(15);
        cameraContainer.setAlignment(Pos.CENTER);

        cameraFeed = new ImageView();
        cameraFeed.setFitWidth(400);
        cameraFeed.setFitHeight(300);
        cameraFeed.setPreserveRatio(true);
        cameraFeed.setStyle("-fx-border-color: #CBD5E1; -fx-border-width: 2px; " +
                "-fx-border-radius: 8px; -fx-background-color: #F1F5F9;");
        cameraFeed.setImage(placeholderImage());

        cameraStatusLabel = new Label("Camera not started");
        cameraStatusLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px;");

        cameraContainer.getChildren().addAll(cameraFeed, cameraStatusLabel);

        root.setCenter(cameraContainer);

        // Control buttons
        startStopButton = new Button("Start Camera");
        startStopButton.setStyle("-fx-background-color: #0d5c36; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 20;");
        startStopButton.setOnAction(e -> toggleCamera());

        Button captureButton = new Button("Capture & Scan");
        captureButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 20;");
        captureButton.setOnAction(e -> captureAndScan());

        Button uploadButton = new Button("Upload QR Image");
        uploadButton.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-border-color: #CBD5E1; -fx-border-radius: 6px; -fx-padding: 8 20;");
        uploadButton.setOnAction(e -> uploadAndScan());

        cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-border-color: #CBD5E1; -fx-border-radius: 6px; -fx-padding: 8 20;");
        cancelButton.setOnAction(e -> {
            stopCamera();
            dialogStage.close();
        });

        VBox buttonBox = new VBox(10, startStopButton, captureButton, uploadButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        root.setCenter(cameraContainer);
        root.setBottom(buttonBox);

        Scene scene = new Scene(root);
        dialogStage.setScene(scene);

        dialogStage.setOnCloseRequest(e -> stopCamera());

        dialogStage.showAndWait();

        return Optional.ofNullable(scannedPayload);
    }

    private Image placeholderImage() {
        int w = 400, h = 300;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = bi.createGraphics();
        g.setColor(Color.web("#F1F5F9").getColor());
        g.fillRect(0, 0, w, h);
        g.setColor(Color.web("#94A3B8").getColor());
        g.drawString("Camera Preview", w / 2 - 40, h / 2);
        g.drawString("(Click Start Camera)", w / 2 - 55, h / 2 + 20);
        g.dispose();
        return SwingFXUtils.toFXImage(bi, null);
    }

    private void toggleCamera() {
        if (scanning.get()) {
            stopCamera();
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                showAlert("No Camera Found", "No webcam detected on this laptop.");
                return;
            }

            // Set a reasonable resolution for QR scanning
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();

            scanning.set(true);
            startStopButton.setText("Stop Camera");
            cameraStatusLabel.setText("Camera active - point at QR code");
            cameraStatusLabel.setStyle("-fx-text-fill: #0d5c36; -fx-font-weight: bold;");

            // Continuous frame capture and QR scanning loop
            new Thread(() -> {
                while (scanning.get()) {
                    try {
                        Thread.sleep(400); // Scan every 400ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (!scanning.get() || webcam == null) break;

                    BufferedImage frame = webcam.getImage();
                    if (frame != null) {
                        // Update the JavaFX ImageView on the UI thread
                        final Image fxImage = SwingFXUtils.toFXImage(frame, null);
                        Platform.runLater(() -> cameraFeed.setImage(fxImage));

                        // Scan the frame for QR code
                        String payload = scanQRCode(frame);
                        if (payload != null && !payload.isEmpty()) {
                            scannedPayload = payload;
                            scanning.set(false);
                            Platform.runLater(() -> {
                                stopCamera();
                                cameraStatusLabel.setText("QR code detected!");
                                cameraStatusLabel.setStyle("-fx-text-fill: #0d5c36; -fx-font-weight: bold;");
                                dialogStage.close();
                            });
                            break;
                        }
                    }
                }
                if (scanning.get()) {
                    Platform.runLater(() -> {
                        startStopButton.setText("Start Camera");
                        cameraStatusLabel.setText("Camera stopped");
                        cameraStatusLabel.setStyle("-fx-text-fill: #64748B;");
                    });
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Camera Error", "Failed to start camera: " + e.getMessage());
        }
    }

    private void stopCamera() {
        scanning.set(false);
        if (webcam != null) {
            webcam.close();
            webcam = null;
        }
        Platform.runLater(() -> {
            startStopButton.setText("Start Camera");
            cameraFeed.setImage(placeholderImage());
            cameraStatusLabel.setText("Camera stopped");
            cameraStatusLabel.setStyle("-fx-text-fill: #64748B;");
        });
    }

    /**
     * Scans a {@link BufferedImage} for a QR code using ZXing's MultiFormatReader.
     *
     * @param image the image to scan
     * @return the decoded QR payload, or null if no QR code is found
     */
    private String scanQRCode(BufferedImage image) {
        try {
            MultiFormatReader reader = new MultiFormatReader();
            BinaryBitmap bitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Result result = reader.decode(bitmap);
            return result.getText();
        } catch (Exception e) {
            // No QR code found in this frame - normal during continuous scanning
            return null;
        }
    }

    /**
     * Opens a file chooser so the driver can select an image file containing
     * a QR code, then scans it using ZXing.
     */
    private void uploadAndScan() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select QR Code Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fileChooser.showOpenDialog(dialogStage);
        if (file != null) {
            try {
                BufferedImage image = ImageIO.read(file);
                String payload = scanQRCode(image);
                if (payload != null && !payload.isEmpty()) {
                    scannedPayload = payload;
                    dialogStage.close();
                } else {
                    cameraStatusLabel.setText("No QR code found in selected image.");
                    cameraStatusLabel.setStyle("-fx-text-fill: #dc2626;");
                }
            } catch (Exception e) {
                e.printStackTrace();
                cameraStatusLabel.setText("Failed to read image file.");
                cameraStatusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
