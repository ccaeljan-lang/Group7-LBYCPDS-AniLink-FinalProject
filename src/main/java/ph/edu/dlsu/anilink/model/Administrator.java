package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Domain model representing an Administrator user within the AniLink system.
 *
 * <p>This class extends {@link User} to specialize functionality for system administrators.
 * It encapsulates administrative metadata including:
 * <ul>
 *   <li><b>Department:</b> Tracks the specific administrative unit or department assigned to the admin.</li>
 *   <li><b>Role Management:</b> Overrides user role resolution to enforce fixed {@code ADMIN} privileges.</li>
 *   <li><b>JSON Serialization:</b> Integrates Jackson annotations ({@link JsonCreator}, {@link JsonProperty})
 *       to support direct JSON mapping during REST interactions with Supabase.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Administrator extends User {

    private String department;

    public Administrator() {
        super();
    }

    @JsonCreator
    public Administrator(
            @JsonProperty("id") Long userId,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("department") String department) {
        super(userId, name, email, password);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }

    @Override
    public String toString() {
        return getName() + " (Admin)";
    }
}