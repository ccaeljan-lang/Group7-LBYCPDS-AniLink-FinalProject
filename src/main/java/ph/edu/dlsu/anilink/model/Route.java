package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Domain model representing a transportation route between two terminals in the AniLink system.
 *
 * <p>This class encapsulates shuttle path information and UI binding logic. It contains:
 * <ul>
 *   <li><b>Identifiers & Terminals:</b> Mapped JSON properties for route ID ({@code id}), starting station ({@code origin}),
 *       and ending station ({@code destination}).</li>
 *   <li><b>JavaFX ComboBox Compatibility:</b> Overridden {@link #equals(Object)} and {@link #hashCode()} methods
 *       leveraging {@code routeId} to ensure correct object matching and selection state in JavaFX controls.</li>
 *   <li><b>Formatted Output:</b> Custom {@link #toString()} method providing bi-directional visual representation
 *       (e.g., "Manila ↔ Laguna") for direct UI rendering.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {

    @JsonProperty("id")
    private Long routeId;

    @JsonProperty("origin")
    private String origin;

    @JsonProperty("destination")
    private String destination;

    public Route() {}

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    // EQUALS & HASHCODE ARE REQUIRED FOR JAVAFX COMBOBOX MATCHING
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Route route = (Route) o;
        return Objects.equals(routeId, route.routeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId);
    }

    @Override
    public String toString() {
        if (origin != null && destination != null) {
            return origin + " ↔ " + destination;
        }
        return "Unknown Route";
    }
}