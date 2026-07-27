package de.leonard.flashcards.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schlanker Health-Endpunkt fuer das Frontend: sagt, ob die Anwendung laeuft
 * und ob die Datenbank erreichbar ist.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final String applicationName;

    public HealthController(DataSource dataSource,
                            @Value("${spring.application.name}") String applicationName) {
        this.dataSource = dataSource;
        this.applicationName = applicationName;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", applicationName, databaseStatus(), Instant.now());
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException e) {
            log.warn("Datenbank nicht erreichbar: {}", e.getMessage());
            return "DOWN";
        }
    }

    public record HealthResponse(String status, String application, String database, Instant timestamp) {
    }
}
