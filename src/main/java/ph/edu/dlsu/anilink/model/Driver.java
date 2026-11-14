package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Domain model representing a Driver user within the AniLink system.
 *
 * <p>This class extends {@link User} to manage credentials and operational attributes for shuttle drivers.
 * It encapsulates the following components:
 * <ul>
 *   <li><b>License Number:</b> Stores the official driving license string assigned to the driver.</li>
 *   <li><b>Role Resolution:</b> Overrides user role resolution to enforce fixed {@code DRIVER} permissions across the system.</li>
 *   <li><b>JSON Mapping:</b> Uses Jackson annotations ({@link JsonCreator}, {@link JsonProperty}) to map properties like
 *       {@code license_number} directly during API requests with Supabase.</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Driver extends User {

    private String licenseNumber;

    public Driver() {
        super();
    }

    @JsonCreator
    public Driver(
            @JsonProperty("id") Long userId,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("license_number") String licenseNumber) {
        super(userId, name, email, password);
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    @Override
    public String getRole() {
        return "DRIVER";
    }

    @Override
    public String toString() {
        return getName() + " (Driver)";
    }
}