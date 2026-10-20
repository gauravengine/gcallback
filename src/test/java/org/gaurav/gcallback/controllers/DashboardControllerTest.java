package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.DashboardStatsResponse;
import org.gaurav.gcallback.services.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(dashboardController, "currentWorkerId", "test-worker-1");
        ReflectionTestUtils.setField(dashboardController, "clock", Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void testGetStats() {
        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalJobs(5)
                .activeJobs(4)
                .totalOccurrences(50)
                .succeededOccurrences(48)
                .failedOccurrences(2)
                .successRate(96.0)
                .avgLatencyMs(115.5)
                .build();

        when(dashboardService.getStats()).thenReturn(stats);

        var res = dashboardController.getStats();
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(5, res.getBody().getData().getTotalJobs());
        assertEquals(96.0, res.getBody().getData().getSuccessRate());
    }

    @Test
    void testGetHealth() {
        var res = dashboardController.getHealth();
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("UP", res.getBody().getData().get("status"));
        assertEquals("test-worker-1", res.getBody().getData().get("currentWorkerId"));
    }
}
