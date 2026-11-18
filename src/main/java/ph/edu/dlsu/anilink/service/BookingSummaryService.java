package ph.edu.dlsu.anilink.service;

import ph.edu.dlsu.anilink.model.Reservation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Service for generating passenger booking confirmation summaries as images.
 *
 * <p>Generates a PNG booking confirmation image containing the reservation
 * details (mirroring Supabase records) and a QR code boarding pass, suitable
 * for download as an image file.
 * </p>
 */
@Service
public class BookingSummaryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final QRService qrService;

    public BookingSummaryService(QRService qrService) {
        this.qrService = qrService;
    }

    /**
     * Generates a booking confirmation summary image for a reservation.
     *
     * <p>The image includes:</p>
     * <ul>
     *   <li>Reservation code and booking reference</li>
     *   <li>Status and booking timestamp</li>
     *   <li>Passenger name and email</li>
     *   <li>Trip route, departure time, and capacity</li>
     *   <li>QR code image for boarding verification</li>
     * </ul>
     *
     * @param reservation the reservation to summarize
     * @return a {@link BufferedImage} PNG-ready booking confirmation image
     */
    public BufferedImage generateSummaryImage(Reservation reservation) {
        int width = 600;
        int height = 950;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // Background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            g.setColor(Color.BLACK);

            Font titleFont = new Font("SansSerif", Font.BOLD, 24);
            Font headerFont = new Font("SansSerif", Font.BOLD, 16);
            Font bodyFont = new Font("SansSerif", Font.PLAIN, 14);

            int x = 40;
            int y = 40;

            // Title
            g.setFont(titleFont);
            g.drawString("ANILINK BOOKING CONFIRMATION", x, y);
            y += 30;

            g.setFont(bodyFont);
            g.drawString("Reservation Code: " +
                    (reservation.getReservationId() != null ? "RES-" + reservation.getReservationId() : "N/A"), x, y);
            y += 25;

            g.drawString("Booking Reference: " +
                    (reservation.getQrPayload() != null ? reservation.getQrPayload() : "N/A"), x, y);
            y += 25;

            g.drawString("Status: " +
                    (reservation.getStatus() != null ? reservation.getStatus() : "N/A"), x, y);
            y += 25;

            g.drawString("Booked At: " +
                    (reservation.getCreatedAt() != null
                            ? reservation.getCreatedAt().format(DATE_TIME_FORMATTER)
                            : "N/A"), x, y);
            y += 40;

            // Passenger details
            g.setFont(headerFont);
            g.drawString("PASSENGER DETAILS", x, y);
            y += 25;
            g.setFont(bodyFont);
            if (reservation.getPassenger() != null) {
                g.drawString("Name: " +
                        (reservation.getPassenger().getName() != null
                                ? reservation.getPassenger().getName() : "N/A"), x, y);
                y += 25;
                g.drawString("Email: " +
                        (reservation.getPassenger().getEmail() != null
                                ? reservation.getPassenger().getEmail() : "N/A"), x, y);
                y += 25;
            }
            y += 15;

            // Trip details
            g.setFont(headerFont);
            g.drawString("TRIP DETAILS", x, y);
            y += 25;
            g.setFont(bodyFont);
            if (reservation.getTrip() != null) {
                if (reservation.getTrip().getRoute() != null) {
                    g.drawString("Route: " +
                            reservation.getTrip().getRoute().getOrigin() + " ↔ " +
                            reservation.getTrip().getRoute().getDestination(), x, y);
                    y += 25;
                }
                if (reservation.getTrip().getSchedule() != null) {
                    g.drawString("Departure: " +
                            reservation.getTrip().getSchedule().getDepartureTime(), x, y);
                    y += 25;
                }
                g.drawString("Trip Status: " +
                        (reservation.getTrip().getStatus() != null
                                ? reservation.getTrip().getStatus() : "N/A"), x, y);
                y += 25;
                g.drawString("Capacity: " + reservation.getTrip().getCapacity() + " seats", x, y);
                y += 25;
                g.drawString("Seats Taken: " + reservation.getTrip().getSeatsTaken(), x, y);
                y += 40;
            }

            // QR Code
            BufferedImage qrImage = qrService.generateQRCode(reservation);
            if (qrImage != null) {
                g.setFont(headerFont);
                g.drawString("BOARDING QR CODE", x, y);
                y += 25;
                int qrSize = 220;
                g.drawImage(qrImage, x, y, qrSize, qrSize, null);
                y += qrSize + 30;
            }

            g.setFont(bodyFont);
            g.drawString("Thank you for using AniLink!", x, y);

        } finally {
            g.dispose();
        }

        return image;
    }

    /**
     * Saves the booking summary image to a file chosen by the user.
     *
     * @param image       the summary image to save
     * @param ownerStage  the owning JavaFX Stage for the file chooser dialog
     */
    public void saveToFile(BufferedImage image, Stage ownerStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Booking Summary");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName("AniLink_Booking_Summary_" +
                System.currentTimeMillis() + ".png");

        File file = fileChooser.showSaveDialog(ownerStage);
        if (file != null) {
            try {
                ImageIO.write(image, "png", file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
