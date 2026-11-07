package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Domain model representing a passenger's position on a trip's waiting list.
 *
 * <p>When a trip reaches full capacity, passengers can join a first-in-first-out
 * (FIFO) queue. This class captures the waiting list entry, including the
 * timestamp used to determine queue order and the current status of the entry.
 * </p>
 */
public class WaitingList {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @JsonProperty("id")
    private Long id;

    @JsonProperty("trip_id")
    private Long tripId;

    @JsonProperty("passenger_id")
    private Long passengerId;

    @JsonProperty("passenger_name")
    private String passengerName;

    @JsonProperty("joined_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime joinedAt;

    @JsonProperty("status")
    private String status;

    public WaitingList() {
        this.status = STATUS_WAITING;
        this.joinedAt = LocalDateTime.now();
    }

    public WaitingList(Long id, Long tripId, Long passengerId, String passengerName,
                       LocalDateTime joinedAt, String status) {
        this.id = id;
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.passengerName = passengerName;
        this.joinedAt = joinedAt;
        this.status = status != null ? status : STATUS_WAITING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(Long passengerId) {
        this.passengerId = passengerId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("WaitingList[id=%d, tripId=%d, passengerId=%d, name=%s, status=%s]",
                id, tripId, passengerId, passengerName, status);
    }
}
