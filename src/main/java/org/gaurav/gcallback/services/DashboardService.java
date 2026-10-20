package org.gaurav.gcallback.services;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.dto.DashboardStatsResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceAttemptRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Log4j2
public class DashboardService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceAttemptRepository occurrenceAttemptRepository;

    @Autowired
    private OccurrenceService occurrenceService;

    @Autowired
    private Clock clock;

    public DashboardStatsResponse getStats() {
        // Jobs metrics
        long totalJobs = jobRepository.countByStatusNot(JobStatus.DELETED);
        long activeJobs = jobRepository.countByStatus(JobStatus.ACTIVE);
        long pausedJobs = jobRepository.countByStatus(JobStatus.PAUSED);
        long completedJobs = jobRepository.countByStatus(JobStatus.COMPLETED);

        // Occurrence metrics
        long totalOccurrences = occurrenceRepository.count();
        long succeeded = occurrenceRepository.countByState(OccurrenceState.SUCCEEDED);
        long failed = occurrenceRepository.countByState(OccurrenceState.FAILED);
        long inProgress = occurrenceRepository.countByState(OccurrenceState.IN_PROGRESS);
        long dispatched = occurrenceRepository.countByState(OccurrenceState.DISPATCHED);
        long pending = occurrenceRepository.countByState(OccurrenceState.PENDING);
        long retryScheduled = occurrenceRepository.countByState(OccurrenceState.RETRY_SCHEDULED);
        long cancelled = occurrenceRepository.countByState(OccurrenceState.CANCELLED);

        // Success rate
        long finishedCount = succeeded + failed;
        double successRate = finishedCount > 0 ? (succeeded * 100.0) / finishedCount : 100.0;
        successRate = Math.round(successRate * 100.0) / 100.0;

        // Average latency
        Double avgLatencyObj = occurrenceAttemptRepository.getAverageLatency();
        double avgLatency = avgLatencyObj != null ? Math.round(avgLatencyObj * 10.0) / 10.0 : 0.0;

        // State distribution
        Map<String, Long> stateDistribution = new LinkedHashMap<>();
        stateDistribution.put("SUCCEEDED", succeeded);
        stateDistribution.put("FAILED", failed);
        stateDistribution.put("IN_PROGRESS", inProgress);
        stateDistribution.put("DISPATCHED", dispatched);
        stateDistribution.put("PENDING", pending);
        stateDistribution.put("RETRY_SCHEDULED", retryScheduled);
        stateDistribution.put("CANCELLED", cancelled);

        // Recent 10 occurrences
        List<OccurrenceResponse> recentOccurrences = occurrenceRepository.findTop10ByOrderByScheduledForDesc().stream()
                .map(occ -> occurrenceService.toOccurrenceResponse(occ, false))
                .toList();

        // Active workers
        List<String> activeWorkers = occurrenceAttemptRepository.findDistinctWorkers();

        // Time series timeline (hourly buckets over last 12 hours)
        Instant now = clock.instant();
        Instant twelveHoursAgo = now.minus(12, ChronoUnit.HOURS);
        List<Occurrence> pastOccurrences = occurrenceRepository.findOccurrencesSince(twelveHoursAgo);

        List<DashboardStatsResponse.TimelinePoint> timeline = buildTimeline(pastOccurrences, now);

        return DashboardStatsResponse.builder()
                .totalJobs(totalJobs)
                .activeJobs(activeJobs)
                .pausedJobs(pausedJobs)
                .completedJobs(completedJobs)
                .totalOccurrences(totalOccurrences)
                .succeededOccurrences(succeeded)
                .failedOccurrences(failed)
                .inProgressOccurrences(inProgress)
                .dispatchedOccurrences(dispatched)
                .pendingOccurrences(pending)
                .retryScheduledOccurrences(retryScheduled)
                .cancelledOccurrences(cancelled)
                .successRate(successRate)
                .avgLatencyMs(avgLatency)
                .timeline(timeline)
                .stateDistribution(stateDistribution)
                .recentOccurrences(recentOccurrences)
                .activeWorkers(activeWorkers)
                .serverTime(now)
                .build();
    }

    private List<DashboardStatsResponse.TimelinePoint> buildTimeline(List<Occurrence> occurrences, Instant now) {
        // Build 12 hourly buckets: [now - 11h, now - 10h, ..., now]
        Map<String, DashboardStatsResponse.TimelinePoint> bucketMap = new LinkedHashMap<>();

        for (int i = 11; i >= 0; i--) {
            Instant bucketStart = now.minus(i, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
            String label = String.format("%02d:00", bucketStart.atZone(java.time.ZoneOffset.UTC).getHour());
            bucketMap.put(label, new DashboardStatsResponse.TimelinePoint(label, 0, 0, 0));
        }

        for (Occurrence occ : occurrences) {
            Instant sch = occ.getScheduledFor();
            String label = String.format("%02d:00", sch.atZone(java.time.ZoneOffset.UTC).getHour());
            DashboardStatsResponse.TimelinePoint point = bucketMap.get(label);
            if (point != null) {
                point.setTotal(point.getTotal() + 1);
                if (occ.getState() == OccurrenceState.SUCCEEDED) {
                    point.setSucceeded(point.getSucceeded() + 1);
                } else if (occ.getState() == OccurrenceState.FAILED) {
                    point.setFailed(point.getFailed() + 1);
                }
            }
        }

        return new ArrayList<>(bucketMap.values());
    }
}
