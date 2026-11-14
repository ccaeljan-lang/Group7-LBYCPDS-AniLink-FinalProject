package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ph.edu.dlsu.anilink.interfaces.Trackable;

import java.util.Objects;

/**
 * Domain model representing an active or scheduled shuttle trip within the AniLink system.
 *
 * <p>Implements {@link Trackable} to support real-time GPS location tracking and operational status updates.
 * It encapsulates the following key components:
 * <ul>
 *   <li><b>Lifecycle Status Constants:</b> Defines standard trip operational states ({@code SCHEDULED},
 *       {@code ARRIVING}, {@code BOARDING}, {@code IN_TRANSIT}, {@code DEPARTED}, {@code COMPLETED}, {@code CANCELLED}).</li>
 *   <li><b>Relational Mappings:</b> Connects to associated domain entities including {@link Route},
 *       {@link DepartureSchedule}, and assigned {@link Driver}.</li>
 *   <li><b>Capacity & Seat Management:</b> Tracks maximum capacity and current occupied seats with business logic
 *       methods ({@link #addPassenger()}, {@link #removePassenger()}, {@link #isFull()}) and Jackson-ignored helper calculations.</li>
 *   <li><b>GPS Coordinates:</b> Stores latitude ({@code current_lat}) and longitude ({@code current_lng}) for live map tracking.</li>
 *   <li><b>UI & Collection Compatibility:</b> Overrides {@link #equals(Object)} and {@link #hashCode()} on {@code tripId}
 *       for accurate JavaFX control selection, alongside a detailed formatted {@link #toString()} representation.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trip implements Trackable {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String ARRIVING = "ARRIVING";
    public static final String BOARDING = "BOARDING";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String DEPARTED = "DEPARTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @JsonProperty("id")
    private Long tripId;

    @JsonProperty("route") // Matches route:routes(*)
    private Route route;

    @JsonProperty("schedule") // Matches schedule:departure_schedules(*)
    private DepartureSchedule schedule;

    @JsonProperty("driver")
    private Driver driver;

    @JsonProperty("capacity")
    private int capacity;

    @JsonProperty("seats_taken")
    private int seatsTaken;

    @JsonProperty("status")
    private String status;

    @JsonProperty("current_lat")
    private double currentLat;

    @JsonProperty("current_lng")
    private double currentLng;

    public Trip() {
        this.status = SCHEDULED;
    }

    public Trip(Long tripId, Route route, DepartureSchedule schedule, int capacity) {
        this.tripId = tripId;
        this.route = route;
        this.schedule = schedule;
        this.capacity = capacity;
        this.seatsTaken = 0;
        this.status = SCHEDULED;
    }

    @Override
    public void updateLocation(double lat, double lng) {
        this.currentLat = lat;
        this.currentLng = lng;
    }

    @Override
    public void updateStatus(String status) {
        this.status = status;
    }

    @JsonIgnore
    public int getAvailableSeats() {
        return Math.max(0, capacity - seatsTaken);
    }

    @JsonIgnore
    public boolean isFull() {
        return seatsTaken >= capacity;
    }

    public boolean addPassenger() {
        if (!isFull()) {
            seatsTaken++;
            return true;
        }
        return false;
    }

    public boolean removePassenger() {
        if (seatsTaken > 0) {
            seatsTaken--;
            return true;
        }
        return false;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public DepartureSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(DepartureSchedule schedule) {
        this.schedule = schedule;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public void setSeatsTaken(int seatsTaken) {
        this.seatsTaken = seatsTaken;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getCurrentLat() {
        return currentLat;
    }

    public void setCurrentLat(double currentLat) {
        this.currentLat = currentLat;
    }

    public double getCurrentLng() {
        return currentLng;
    }

    public void setCurrentLng(double currentLng) {
        this.currentLng = currentLng;
    }

    // Required for JavaFX ListView & ComboBox comparison matching
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trip trip = (Trip) o;
        return Objects.equals(tripId, trip.tripId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId);
    }

    @Override
    public String toString() {
        // FIXED: Uses getOrigin() and getDestination() instead of getRouteName()
        String routeInfo = (route != null)
                ? route.getOrigin() + " ↔ " + route.getDestination()
                : "Unknown Route";

        String timeInfo = (schedule != null && schedule.getDepartureTime() != null)
                ? schedule.getDepartureTime().toString()
                : "TBD";

        return String.format("TRIP #%d | %s | %s | Seats: %d/%d | [%s]",
                tripId != null ? tripId : 0,
                routeInfo,
                timeInfo,
                seatsTaken,
                capacity,
                status != null ? status : "SCHEDULED");
    }
}