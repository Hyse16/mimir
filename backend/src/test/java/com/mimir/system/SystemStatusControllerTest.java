package com.mimir.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemStatusControllerTest {

    @Test
    void reportsDatabaseUpWithoutExposingConnectionDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT TRUE", Boolean.class)).thenReturn(true);

        var response = new SystemStatusController(jdbcTemplate).status().getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.privacyMode()).isEqualTo("LOCAL_ONLY");
        assertThat(response.components()).containsEntry("database", "UP");
        assertThat(response.toString()).doesNotContain("password", "jdbc:");
    }

    @Test
    void reportsDegradedWhenDatabaseCheckFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT TRUE", Boolean.class))
                .thenThrow(new IllegalStateException("database unavailable"));

        var response = new SystemStatusController(jdbcTemplate).status().getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DEGRADED");
        assertThat(response.components()).containsEntry("database", "DOWN");
    }
}
