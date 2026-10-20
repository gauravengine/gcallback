package org.gaurav.gcallback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    // Job stats
    private long totalJobs;
    private long activeJobs;
    private long pausedJobs;
    private long completedJobs;

    // Occurrence stats
    private long totalOccurrences;
    private long succeededOccurrences;
    private long failedOccurrences;
    private long inProgressOccurrences;
    private long dispatchedOccurrences;
    private long pendingOccurrences;
    private long retryScheduledOccurrences;
    private long cancelledOccurrences;

    // Rates & performance
    private double successRate;
    private double avgLatencyMs;

    // Timeline series for trend charts
    private List<TimelinePoint> timeline;

    // State distribution for breakdown charts
    private Map<String, Long> stateDistribution;

    // Recent 10 occurrences
    private List<OccurrenceResponse> recentOccurrences;

    // Active workers
    private List<String> activeWorkers;

    // Current Server UTC Time
    private Instant serverTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelinePoint {
        private String label;
        private long succeeded;
        private long failed;
        private long total;
    }
}
