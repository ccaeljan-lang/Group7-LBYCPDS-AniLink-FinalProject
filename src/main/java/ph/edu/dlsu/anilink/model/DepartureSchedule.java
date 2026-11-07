package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


/**
 * Domain model representing a scheduled departure time within the AniLink system.
 *
 * <p>This class manages the timetable configuration for shuttle services. It encapsulates:
 * <ul>
 *   <li><b>Schedule Identification:</b> Unique database identifier ({@code scheduleId}) mapped via {@link JsonProperty}.</li>
 *   <li><b>Route Binding:</b> The associated {@link Route} defining origin and destination terminals.</li>
 *   <li><b>Temporal Data:</b> Departure time using {@link LocalTime}, serialized/deserialized with
 *       {@link JsonFormat} in 24-hour {@code HH:mm:ss} format.</li>
 *   <li><b>Capacity Constraints:</b> Standard passenger capacity limit configured for trips on this timetable.</li>
 *   <li><b>String Formatting:</b> Human-readable output formatting 12-hour time (e.g., "08:30 AM")
 *       with route information for UI components.</li>
 * </ul>
 * </p>
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class DepartureSchedule {
    @JsonProperty("id")
    private Long scheduleId;

    @JsonProperty("route")
    private Route route;

    @JsonProperty("departure_time")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime departureTime;

    @JsonProperty("capacity")
    private int capacity;

    // Default constructor required for Jackson reflection
    public DepartureSchedule() {
    }

    public DepartureSchedule(Long scheduleId, Route route, LocalTime departureTime, int capacity) {
        this.scheduleId = scheduleId;
        this.route = route;
        this.departureTime = departureTime;
        this.capacity = capacity;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public String toString() {
        String formattedTime = (departureTime != null)
                ? departureTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
                : "N/A";

        if (route != null) {
            return String.format("[%s] %s (Capacity: %d)", formattedTime, route.toString(), capacity);
        }
        return String.format("[%s] (Capacity: %d)", formattedTime, capacity);
    }
}