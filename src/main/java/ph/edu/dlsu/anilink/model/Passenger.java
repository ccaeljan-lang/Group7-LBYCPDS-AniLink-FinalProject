package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Domain model representing a Passenger user within the AniLink system.
 *
 * <p>This class extends {@link User} to manage passenger identity and reservation privileges.
 * It encapsulates the following components:
 * <ul>
 *   <li><b>ID Number:</b> Stores the official institutional ID number (e.g., DLSU ID Number 121XXXXX).</li>
 *   <li><b>Role Resolution:</b> Overrides user role resolution to enforce fixed {@code PASSENGER} permissions across the platform.</li>
 *   <li><b>JSON Serialization:</b> Integrates Jackson annotations ({@link JsonCreator}, {@link JsonProperty}) to map the
 *       {@code id_number} field directly during API payloads with Supabase.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Passenger extends User {

    private String idNumber; // e.g., DLSU ID Number (121XXXXX)

    public Passenger() {
        super();
    }

    @JsonCreator
    public Passenger(
            @JsonProperty("id") Long userId,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("id_number") String idNumber) {
        super(userId, name, email, password);
        this.idNumber = idNumber;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    @Override
    public String getRole() {
        return "PASSENGER";
    }

    @Override
    public String toString() {
        return getName() + " (Passenger - " + getEmail() + ")";
    }
}