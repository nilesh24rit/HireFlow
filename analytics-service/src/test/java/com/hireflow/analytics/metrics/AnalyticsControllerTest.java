package com.hireflow.analytics.metrics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AnalyticsControllerTest {

    @Test
    void rejectsReversedReportingPeriod() {
        AnalyticsController controller = new AnalyticsController(mock(AnalyticsRepository.class));

        assertThatThrownBy(() -> controller.overview(Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
