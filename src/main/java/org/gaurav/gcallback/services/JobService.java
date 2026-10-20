package org.gaurav.gcallback.services;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.dao.models.OccurrenceAttempt;
import org.gaurav.gcallback.dto.JobRequest;
import org.gaurav.gcallback.dto.JobResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.enums.ScheduleType;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceAttemptRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.gaurav.gcallback.utils.NextFireTimeCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Service
@Log4j2
public class JobService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceAttemptRepository occurrenceAttemptRepository;

    @Autowired
    private NextFireTimeCalculator nextFireTimeCalculator;

    @Autowired
    private Clock clock;

    public PagedResponse<JobResponse> getJobs(JobStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Job> jobPage;

        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (hasSearch) {
            String q = search.trim();
            if (status != null) {
                jobPage = jobRepository.searchJobsWithStatus(q, status, pageable);
            } else {
                jobPage = jobRepository.searchJobs(q, JobStatus.DELETED, pageable);
            }
        } else {
            if (status != null) {
                jobPage = jobRepository.findByStatus(status, pageable);
            } else {
                jobPage = jobRepository.findByStatusNot(JobStatus.DELETED, pageable);
            }
        }

        List<JobResponse> responses = jobPage.getContent().stream()
                .map(this::toJobResponse)
                .toList();

        return PagedResponse.of(responses, jobPage.getNumber(), jobPage.getSize(), jobPage.getTotalElements());
    }

    public JobResponse getJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse createJob(JobRequest req) {
        validateRequest(req);

        Job job = new Job();
        applyRequestToJob(job, req);
        job.setStatus(JobStatus.ACTIVE);

        if (job.getScheduleType() == ScheduleType.CRON) {
            job.setNextFireTime(nextFireTimeCalculator.firstFireTime(job));
        } else {
            job.setNextFireTime(job.getRunAt() != null ? job.getRunAt() : clock.instant());
        }

        job = jobRepository.save(job);
        log.info("Created job: id={}, name={}, nextFireTime={}", job.getId(), job.getName(), job.getNextFireTime());
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse updateJob(Long id, JobRequest req) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));

        validateRequest(req);

        boolean scheduleChanged = (job.getScheduleType() != req.getScheduleType())
                || !Objects.equals(job.getCronExpression(), req.getCronExpression())
                || !Objects.equals(job.getTimeZone(), req.getTimeZone())
                || !Objects.equals(job.getRunAt(), req.getRunAt());

        applyRequestToJob(job, req);

        if (scheduleChanged && job.getStatus() == JobStatus.ACTIVE) {
            if (job.getScheduleType() == ScheduleType.CRON) {
                job.setNextFireTime(nextFireTimeCalculator.firstFireTime(job));
            } else {
                job.setNextFireTime(job.getRunAt() != null ? job.getRunAt() : clock.instant());
            }
        }

        job = jobRepository.save(job);
        log.info("Updated job: id={}, nextFireTime={}", job.getId(), job.getNextFireTime());
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse pauseJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));
        job.setStatus(JobStatus.PAUSED);
        job = jobRepository.save(job);
        log.info("Paused job: id={}", job.getId());
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse resumeJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));
        job.setStatus(JobStatus.ACTIVE);

        Instant now = clock.instant();
        if (job.getNextFireTime() == null || job.getNextFireTime().isBefore(now)) {
            if (job.getScheduleType() == ScheduleType.CRON) {
                job.setNextFireTime(nextFireTimeCalculator.firstFireTime(job));
            } else {
                job.setNextFireTime(job.getRunAt() != null && job.getRunAt().isAfter(now) ? job.getRunAt() : now);
            }
        }

        job = jobRepository.save(job);
        log.info("Resumed job: id={}, nextFireTime={}", job.getId(), job.getNextFireTime());
        return toJobResponse(job);
    }

    @Transactional
    public OccurrenceResponse triggerJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));

        if (job.getStatus() == JobStatus.DELETED) {
            throw new IllegalStateException("Cannot trigger a deleted job");
        }

        Instant now = clock.instant();
        Occurrence occurrence = new Occurrence();
        occurrence.setJobId(job.getId());
        occurrence.setScheduledFor(now);
        occurrence.setState(OccurrenceState.PENDING);
        occurrence.setNextAttemptAt(now);
        occurrence.setAttemptCount(0);

        occurrence = occurrenceRepository.saveAndFlush(occurrence);
        log.info("Triggered immediate execution for job {}: occurrence={}", job.getId(), occurrence.getId());

        return OccurrenceResponse.builder()
                .id(occurrence.getId())
                .jobId(job.getId())
                .jobName(job.getName())
                .httpMethod(job.getHttpMethod())
                .httpUrl(job.getHttpUrl())
                .scheduledFor(occurrence.getScheduledFor())
                .state(occurrence.getState())
                .attemptCount(0)
                .maxAttempts(job.getMaxAttempts())
                .nextAttemptAt(occurrence.getNextAttemptAt())
                .createdAt(occurrence.getCreatedAt())
                .updatedAt(occurrence.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteJob(Long id, boolean hardDelete) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));

        if (hardDelete) {
            List<Occurrence> occurrences = occurrenceRepository.findByJobId(id);
            for (Occurrence occ : occurrences) {
                List<OccurrenceAttempt> attempts = occurrenceAttemptRepository.findByOccurrenceIdOrderByAttemptNumber(occ.getId());
                occurrenceAttemptRepository.deleteAll(attempts);
            }
            occurrenceRepository.deleteAll(occurrences);
            jobRepository.delete(job);
            log.info("Hard deleted job id={}", id);
        } else {
            job.setStatus(JobStatus.DELETED);
            job.setNextFireTime(null);
            jobRepository.save(job);

            // Cancel any pending occurrences
            List<Occurrence> pending = occurrenceRepository.findByJobId(id);
            for (Occurrence o : pending) {
                if (o.getState() == OccurrenceState.PENDING || o.getState() == OccurrenceState.RETRY_SCHEDULED) {
                    o.setState(OccurrenceState.CANCELLED);
                    o.setNextAttemptAt(null);
                    occurrenceRepository.save(o);
                }
            }
            log.info("Soft deleted job id={}", id);
        }
    }

    @Transactional
    public void seedDemoJobs() {
        if (jobRepository.count() > 0) {
            return;
        }

        Instant now = clock.instant();

        // 1. Stripe Payment Webhook
        Job job1 = new Job();
        job1.setName("Stripe Payment Webhook");
        job1.setScheduleType(ScheduleType.CRON);
        job1.setCronExpression("0 */2 * * * *"); // Every 2 minutes
        job1.setTimeZone("UTC");
        job1.setHttpMethod("POST");
        job1.setHttpUrl("https://httpbin.org/post");
        job1.setHttpHeaders(Map.of("Content-Type", "application/json", "X-Webhook-Secret", "sec_live_9f8d7c6b5a4"));
        job1.setHttpQueryParams(Map.of("source", "stripe", "env", "production"));
        job1.setHttpBody("{\"event\":\"payment_intent.succeeded\",\"amount\":14900,\"currency\":\"usd\",\"customer\":\"cus_N3x98aBC\"}");
        job1.setMaxAttempts(3);
        job1.setTimeoutMs(5000);
        job1.setStatus(JobStatus.ACTIVE);
        job1.setNextFireTime(nextFireTimeCalculator.firstFireTime(job1));
        jobRepository.save(job1);

        // 2. Daily User Analytics Report
        Job job2 = new Job();
        job2.setName("Daily Analytics Aggregator");
        job2.setScheduleType(ScheduleType.CRON);
        job2.setCronExpression("0 0 0 * * *"); // Daily at midnight
        job2.setTimeZone("UTC");
        job2.setHttpMethod("POST");
        job2.setHttpUrl("https://httpbin.org/anything");
        job2.setHttpHeaders(Map.of("Content-Type", "application/json", "Authorization", "Bearer eyJhbGciOi..."));
        job2.setHttpQueryParams(Map.of("report", "daily_summary"));
        job2.setHttpBody("{\"metrics\":[\"active_users\",\"conversion_rate\",\"error_rate\"],\"format\":\"json\"}");
        job2.setMaxAttempts(5);
        job2.setTimeoutMs(10000);
        job2.setStatus(JobStatus.ACTIVE);
        job2.setNextFireTime(nextFireTimeCalculator.firstFireTime(job2));
        jobRepository.save(job2);

        // 3. User Welcome Email Callback (One-off)
        Job job3 = new Job();
        job3.setName("Onboarding Welcome Email Dispatch");
        job3.setScheduleType(ScheduleType.ONE_OFF);
        job3.setTimeZone("UTC");
        job3.setRunAt(now.plusSeconds(300));
        job3.setHttpMethod("POST");
        job3.setHttpUrl("https://httpbin.org/post");
        job3.setHttpHeaders(Map.of("Content-Type", "application/json"));
        job3.setHttpBody("{\"user_id\":98412,\"template\":\"welcome_series_v2\",\"email\":\"developer@example.com\"}");
        job3.setMaxAttempts(3);
        job3.setTimeoutMs(4000);
        job3.setStatus(JobStatus.ACTIVE);
        job3.setNextFireTime(job3.getRunAt());
        jobRepository.save(job3);

        // 4. External Health Check Ping
        Job job4 = new Job();
        job4.setName("Partner API Health Check");
        job4.setScheduleType(ScheduleType.CRON);
        job4.setCronExpression("0 */5 * * * *"); // Every 5 minutes
        job4.setTimeZone("UTC");
        job4.setHttpMethod("GET");
        job4.setHttpUrl("https://httpbin.org/status/200");
        job4.setHttpHeaders(Map.of("Accept", "application/json"));
        job4.setMaxAttempts(2);
        job4.setTimeoutMs(3000);
        job4.setStatus(JobStatus.ACTIVE);
        job4.setNextFireTime(nextFireTimeCalculator.firstFireTime(job4));
        jobRepository.save(job4);

        log.info("Seeded 4 demo jobs into the database.");
    }

    private void validateRequest(JobRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Job name is required");
        }
        if (req.getHttpUrl() == null || req.getHttpUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP URL is required");
        }
        if (req.getScheduleType() == null) {
            throw new IllegalArgumentException("Schedule type (CRON or ONE_OFF) is required");
        }

        if (req.getScheduleType() == ScheduleType.CRON) {
            if (req.getCronExpression() == null || req.getCronExpression().trim().isEmpty()) {
                throw new IllegalArgumentException("Cron expression is required for CRON schedule type");
            }
            try {
                CronExpression.parse(req.getCronExpression().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid 6-field Spring cron expression: " + e.getMessage());
            }

            if (req.getTimeZone() != null && !req.getTimeZone().trim().isEmpty()) {
                try {
                    ZoneId.of(req.getTimeZone().trim());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid timezone identifier: " + req.getTimeZone());
                }
            }
        }
    }

    private void applyRequestToJob(Job job, JobRequest req) {
        job.setName(req.getName().trim());
        job.setScheduleType(req.getScheduleType());
        job.setCronExpression(req.getCronExpression() != null ? req.getCronExpression().trim() : null);
        job.setTimeZone(req.getTimeZone() != null && !req.getTimeZone().trim().isEmpty() ? req.getTimeZone().trim() : "UTC");
        job.setRunAt(req.getRunAt());
        job.setHttpMethod(req.getHttpMethod() != null ? req.getHttpMethod().trim().toUpperCase() : "POST");
        job.setHttpUrl(req.getHttpUrl().trim());
        job.setHttpHeaders(req.getHttpHeaders() != null ? req.getHttpHeaders() : new HashMap<>());
        job.setHttpQueryParams(req.getHttpQueryParams() != null ? req.getHttpQueryParams() : new HashMap<>());
        job.setHttpBody(req.getHttpBody());
        job.setMaxAttempts(req.getMaxAttempts() > 0 ? req.getMaxAttempts() : 3);
        job.setTimeoutMs(req.getTimeoutMs() > 0 ? req.getTimeoutMs() : 5000);
    }

    public JobResponse toJobResponse(Job job) {
        long total = occurrenceRepository.countByJobId(job.getId());
        long success = occurrenceRepository.countByJobIdAndState(job.getId(), OccurrenceState.SUCCEEDED);
        long failed = occurrenceRepository.countByJobIdAndState(job.getId(), OccurrenceState.FAILED);

        String lastStatus = null;
        if (total > 0) {
            Page<Occurrence> latestOccPage = occurrenceRepository.findByJobIdOrderByScheduledForDesc(job.getId(), PageRequest.of(0, 1));
            if (!latestOccPage.isEmpty()) {
                lastStatus = latestOccPage.getContent().get(0).getState().name();
            }
        }

        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .scheduleType(job.getScheduleType())
                .cronExpression(job.getCronExpression())
                .timeZone(job.getTimeZone())
                .runAt(job.getRunAt())
                .httpMethod(job.getHttpMethod())
                .httpUrl(job.getHttpUrl())
                .httpHeaders(job.getHttpHeaders())
                .httpQueryParams(job.getHttpQueryParams())
                .httpBody(job.getHttpBody())
                .maxAttempts(job.getMaxAttempts())
                .timeoutMs(job.getTimeoutMs())
                .status(job.getStatus())
                .nextFireTime(job.getNextFireTime())
                .lastFireTime(job.getLastFireTime())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .totalOccurrences(total)
                .successfulOccurrences(success)
                .failedOccurrences(failed)
                .lastExecutionStatus(lastStatus)
                .build();
    }
}
