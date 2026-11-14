package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Domain model representing a passenger reservation within the AniLink system.
 *
 * <p>This class manages ticket bookings and lifecycle state transitions for shuttle trips.
 * It encapsulates the following components:
 * <ul>
 *   <li><b>Status Constants:</b> Enforces standard booking states ({@code CONFIRMED}, {@code WAITLISTED},
 *       {@code VERIFIED}, {@code BOARDED}, {@code CANCELLED}, {@code COMPLETED}).</li>
 *   <li><b>Domain Associations:</b> Binds the reserving {@link Passenger} to their requested {@link Trip}.</li>
 *   <li><b>QR Code Integration:</b> Generates and stores a unique payload string ({@code qrPayload})
 *       used for boarding verification scanners.</li>
 *   <li><b>State Helper Methods:</b> Exposes clear state-transition logic ({@link #confirm()}, {@link #cancel()}, etc.)
 *       to mutate booking status cleanly.</li>
 *   <li><b>JSON Serialization:</b> Uses Jackson annotations ({@link JsonProperty}, {@link JsonFormat}) to map
 *       fields like {@code created_at} and nested objects to Supabase REST payloads.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Reservation {

    public static final String CONFIRMED = "CONFIRMED";
    public static final String WAITLISTED = "WAITLISTED";
    public static final String VERIFIED = "VERIFIED";
    public static final String BOARDED = "BOARDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";

    @JsonProperty("id")
    private Long reservationId;

    @JsonProperty("passenger")
    private Passenger passenger;

    @JsonProperty("trip")
    private Trip trip;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;

    @JsonProperty("status")
    private String status;

    @JsonProperty("qr_payload")
    private String qrPayload;

    // Default constructor required for Jackson reflection
    public Reservation() {
        this.status = WAITLISTED;
    }

    public Reservation(Long reservationId, Passenger passenger, Trip trip) {
        this.reservationId = reservationId;
        this.passenger = passenger;
        this.trip = trip;
        this.createdAt = LocalDateTime.now();
        this.status = WAITLISTED;
        this.qrPayload = "ANILINK-RES-" + reservationId;
    }

    public void confirm() {
        this.status = CONFIRMED;
    }

    public void waitlist() {
        this.status = WAITLISTED;
    }

    public void cancel() {
        this.status = CANCELLED;
    }

    public void verify() {
        this.status = VERIFIED;
    }

    public void complete() {
        this.status = COMPLETED;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public void setPassenger(Passenger passenger) {
        this.passenger = passenger;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    @Override
    public String toString() {
        String passengerName = (passenger != null) ? passenger.getName() : "Unknown Passenger";
        return String.format("RES-%d | %s | Status: %s", reservationId, passengerName, status);
    }
}