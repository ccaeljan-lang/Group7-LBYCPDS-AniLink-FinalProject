package ph.edu.dlsu.anilink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "role",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Passenger.class, name = "PASSENGER"),
        @JsonSubTypes.Type(value = Driver.class, name = "DRIVER"),
        @JsonSubTypes.Type(value = Administrator.class, name = "ADMIN")
})

/**
 * Abstract base domain model representing a user in the AniLink system.
 *
 * <p>This class serves as the root of the user hierarchy and encapsulates core identity attributes,
 * validation routines, and Jackson polymorphic deserialization annotations. Key components include:
 * <ul>
 *   <li><b>Polymorphic Deserialization:</b> Annotated with {@link JsonTypeInfo} and {@link JsonSubTypes}
 *       to automatically map JSON payloads into concrete subtypes ({@link Passenger}, {@link Driver},
 *       or {@link Administrator}) based on the {@code role} property from Supabase.</li>
 *   <li><b>Core Profile Attributes:</b> Encapsulates user ID ({@code id}), full name, credentials, and email.</li>
 *   <li><b>Domain Validation:</b> Enforces business invariants, ensuring non-empty name and password fields,
 *       as well as mandatory {@code @dlsu.edu.ph} email domain constraints.</li>
 *   <li><b>Abstract Role Enforcement:</b> Declares {@link #getRole()} to mandate explicit role definition in subclasses.</li>
 * </ul>
 * </p>
 */
public abstract class User {

    @JsonProperty("id")
    private Long userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("password")
    private String password;

    // Default constructor for Jackson reflection/deserialization
    public User() {
    }

    public User(Long userId, String name, String email, String password) {
        this.userId = userId;
        setName(name);
        setEmail(email);
        setPassword(password);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        this.name = name.trim();
    }

    public void setEmail(String email) {
        if (email == null || !email.toLowerCase().endsWith("@dlsu.edu.ph")) {
            throw new IllegalArgumentException("Email must be a valid DLSU email.");
        }
        this.email = email;
    }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        this.password = password;
    }

    public String extractUsername() {
        int atIndex = email.indexOf("@");
        if (atIndex == -1) {
            return email;
        }
        return email.substring(0, atIndex);
    }

    public abstract String getRole();
}