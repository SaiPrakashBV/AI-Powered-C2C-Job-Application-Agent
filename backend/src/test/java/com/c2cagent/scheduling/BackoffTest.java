package com.c2cagent.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.c2cagent.configuration.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BackoffTest {
    @Test
    void backoffGrowsExponentiallyAndIsBounded() {
        var props = new AppProperties(Path.of("."), false, new AppProperties.Security(5, Duration.ofMinutes(15), 12),
                new AppProperties.Jobs(true, 5, Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(30)));
        var service = new BackgroundJobService(mock(BackgroundJobRepository.class), mock(JdbcTemplate.class), props,
                Optional.empty());
        assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(service.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(service.backoff(4)).isEqualTo(Duration.ofSeconds(80));
        assertThat(service.backoff(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.backoff(500)).isEqualTo(Duration.ofMinutes(5));
    }
}
