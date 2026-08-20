package com.mimir.system;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final JdbcTemplate jdbcTemplate;

    public SystemStatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        String database = databaseAvailable() ? "UP" : "DOWN";
        String status = "UP".equals(database) ? "UP" : "DEGRADED";
        return ResponseEntity.ok(new SystemStatusResponse(
                status,
                "LOCAL_ONLY",
                Map.of("database", database)));
    }

    private boolean databaseAvailable() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT TRUE", Boolean.class));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record SystemStatusResponse(
            String status,
            String privacyMode,
            Map<String, String> components) {
    }
}
