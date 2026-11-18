package ph.edu.dlsu.anilink.controller;

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
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ph.edu.dlsu.anilink.service.SupabaseService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera scanner dialog for drivers to scan passenger QR codes during boarding.
 *
 * <p>Opens a modal window with a camera preview area. The driver can capture
 * a frame (or upload an image containing a QR code) and the scanner uses ZXing
 * to decode it. When a valid QR code is detected, the scanner returns the
 * payload so the driver can verify and board the passenger.</p>
 */
public class CameraScannerDialog {

    private final Stage ownerStage;
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private Stage dialogStage;
    private Label cameraStatusLabel;
    private Button startStopButton;
    private Button cancelButton;

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
        dialogStage.setMinWidth(520);
        dialogStage.setMinHeight(520);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F8FAFC;");
        root.setPadding(new Insets(20));

        // Camera preview area
        VBox cameraContainer = new VBox(15);
        cameraContainer.setAlignment(Pos.CENTER);

        Label cameraFeed = new Label();
        cameraFeed.setPrefSize(380, 280);
        cameraFeed.setStyle("-fx-border-color: #CBD5E1; -fx-border-width: 2px; " +
                "-fx-border-radius: 8px; -fx-background-color: #F1F5F9; " +
                "-fx-alignment: CENTER; -fx-text-fill: #94A3B8; -fx-font-size: 14px;");
        cameraFeed.setText("📷 Camera Preview\n\n(Point camera at passenger QR code\nand click Capture & Scan)");

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
        captureButton.setOnAction(e -> {
            String payload = captureAndScanFrame();
            if (payload != null && !payload.isEmpty()) {
                scannedPayload = payload;
                dialogStage.close();
            } else {
                cameraStatusLabel.setText("No QR code detected in frame. Try again.");
                cameraStatusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        Button uploadButton = new Button("Upload QR Image");
        uploadButton.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-border-color: #CBD5E1; -fx-border-radius: 6px; -fx-padding: 8 20;");
        uploadButton.setOnAction(e -> uploadAndScan());

        cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; " +
                "-fx-border-color: #CBD5E1; -fx-border-radius: 6px; -fx-padding: 8 20;");
        cancelButton.setOnAction(e -> {
            scanning.set(false);
            dialogStage.close();
        });

        VBox buttonBox = new VBox(10, startStopButton, captureButton, uploadButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        root.setCenter(cameraContainer);
        root.setBottom(buttonBox);

        Scene scene = new Scene(root);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();

        return Optional.ofNullable(scannedPayload);
    }

    private void toggleCamera() {
        if (scanning.get()) {
            stopCamera();
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        scanning.set(true);
        startStopButton.setText("Stop Camera");
        cameraStatusLabel.setText("Camera active - point at QR code");
        cameraStatusLabel.setStyle("-fx-text-fill: #0d5c36; -fx-font-weight: bold;");
    }

    private void stopCamera() {
        scanning.set(false);
        startStopButton.setText("Start Camera");
        cameraStatusLabel.setText("Camera stopped");
        cameraStatusLabel.setStyle("-fx-text-fill: #64748B;");
    }

    /**
     * Captures a frame from the camera preview and scans it for a QR code using ZXing.
     * <p>When a real camera integration is available (e.g., OpenCV or JavaCV),
     * replace the placeholder frame capture with actual webcam frame reading.</p>
     *
     * @return the decoded QR payload, or null if no QR code is found
     */
    private String captureAndScanFrame() {
        // Placeholder: In production, replace this with actual webcam frame capture.
        // The frame would be read as a BufferedImage and passed to scanQRCode().
        return null;
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
                BufferedImage image = javax.imageio.ImageIO.read(file);
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
            return null;
        }
    }
}
