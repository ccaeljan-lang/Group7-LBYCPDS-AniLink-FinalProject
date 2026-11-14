package ph.edu.dlsu.anilink.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The main entry point for the AniLink Spring Boot application.
 * <p>
 * This class is responsible for bootstrapping the application context and
 * starting the embedded web server. It also provides base configuration beans,
 * such as a custom {@link ObjectMapper} that supports the Java 8 Date/Time API (JSR-310).
 * </p>
 */
@SpringBootApplication(scanBasePackages = "ph.edu.dlsu.anilink")
public class AniLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AniLinkApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        return objectMapper;
    }
}