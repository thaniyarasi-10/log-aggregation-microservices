package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriorityDeadlineResolverTest {

    private PriorityDeadlineResolver resolver;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        resolver = new PriorityDeadlineResolver();
        baseTime = LocalDateTime.of(2026, 5, 29, 12, 0, 0);
    }

    @Test
    void resolveDueDate_critical_returnsPlus4Hours() {
        LocalDateTime resolved = resolver.resolveDueDate("CRITICAL", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusHours(4));
    }

    @Test
    void resolveDueDate_high_returnsPlus1Day() {
        LocalDateTime resolved = resolver.resolveDueDate("HIGH", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusDays(1));
    }

    @Test
    void resolveDueDate_medium_returnsPlus3Days() {
        LocalDateTime resolved = resolver.resolveDueDate("MEDIUM", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusDays(3));
    }

    @Test
    void resolveDueDate_low_returnsPlus7Days() {
        LocalDateTime resolved = resolver.resolveDueDate("LOW", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusDays(7));
    }

    @Test
    void resolveDueDate_unknown_defaultsToMedium() {
        LocalDateTime resolved = resolver.resolveDueDate("INVALID", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusDays(3));
    }

    @Test
    void resolveDueDate_null_defaultsToMedium() {
        LocalDateTime resolved = resolver.resolveDueDate(null, baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusDays(3));
    }

    @Test
    void resolveDueDate_caseInsensitive_resolvesCorrectly() {
        LocalDateTime resolved = resolver.resolveDueDate("critical", baseTime);
        assertThat(resolved).isEqualTo(baseTime.plusHours(4));
    }
}
