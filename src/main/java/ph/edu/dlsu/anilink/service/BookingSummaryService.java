package ph.edu.dlsu.anilink.service;

import org.springframework.stereotype.Service;
import ph.edu.dlsu.anilink.model.Reservation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating passenger booking confirmation summaries.
 *
 * <p>Generates a formatted text summary of a {@link Reservation} that mirrors the
 * information stored in Supabase, suitable for display or download as a file.
 * </p>
 */
@Service
public class BookingSummaryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Generates a formatted booking confirmation summary for a reservation.
     *
     * @param reservation the reservation to summarize
     * @return formatted summary text
     */
    public String generateSummary(Reservation reservation) {
        StringBuilder sb = new StringBuilder();

        sb.append("========================================\n");
        sb.append("        ANILINK BOOKING CONFIRMATION    \n");
        sb.append("========================================\n\n");

        sb.append("Reservation Code: ")
                .append(reservation.getReservationId() != null ? "RES-" + reservation.getReservationId() : "N/A")
                .append("\n");
        sb.append("Booking Reference: ")
                .append(reservation.getQrPayload() != null ? reservation.getQrPayload() : "N/A")
                .append("\n");
        sb.append("Status: ")
                .append(reservation.getStatus() != null ? reservation.getStatus() : "N/A")
                .append("\n");
        sb.append("Booked At: ")
                .append(reservation.getCreatedAt() != null
                        ? reservation.getCreatedAt().format(DATE_TIME_FORMATTER)
                        : "N/A")
                .append("\n\n");

        if (reservation.getPassenger() != null) {
            sb.append("PASSENGER DETAILS\n");
            sb.append("-----------------\n");
            sb.append("Name: ")
                    .append(reservation.getPassenger().getName() != null ? reservation.getPassenger().getName() : "N/A")
                    .append("\n");
            sb.append("Email: ")
                    .append(reservation.getPassenger().getEmail() != null ? reservation.getPassenger().getEmail() : "N/A")
                    .append("\n\n");
        }

        if (reservation.getTrip() != null) {
            sb.append("TRIP DETAILS\n");
            sb.append("------------\n");

            if (reservation.getTrip().getRoute() != null) {
                sb.append("Route: ")
                        .append(reservation.getTrip().getRoute().getOrigin() != null
                                ? reservation.getTrip().getRoute().getOrigin() : "N/A")
                        .append(" ↔ ")
                        .append(reservation.getTrip().getRoute().getDestination() != null
                                ? reservation.getTrip().getRoute().getDestination() : "N/A")
                        .append("\n");
            }

            if (reservation.getTrip().getSchedule() != null) {
                sb.append("Departure Time: ")
                        .append(reservation.getTrip().getSchedule().getDepartureTime() != null
                                ? reservation.getTrip().getSchedule().getDepartureTime().toString()
                                : "N/A")
                        .append("\n");
            }

            sb.append("Trip Status: ")
                    .append(reservation.getTrip().getStatus() != null
                            ? reservation.getTrip().getStatus() : "N/A")
                    .append("\n");
            sb.append("Capacity: ")
                    .append(reservation.getTrip().getCapacity())
                    .append(" seats\n");
            sb.append("Seats Taken: ")
                    .append(reservation.getTrip().getSeatsTaken())
                    .append("\n\n");
        }

        sb.append("========================================\n");
        sb.append("       Thank you for using AniLink!     \n");
        sb.append("========================================\n");

        return sb.toString();
    }

    /**
     * Saves the booking summary text to a file chosen by the user.
     *
     * @param content   the summary text to save
     * @param ownerStage the owning JavaFX Stage for the file chooser dialog
     */
    public void saveToFile(String content, javafx.stage.Stage ownerStage) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Booking Summary");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName("AniLink_Booking_Summary_" +
                (System.currentTimeMillis()) + ".txt");

        File file = fileChooser.showSaveDialog(ownerStage);
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
