package org.gaurav.gcallback.scanners;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.config.SchedulerConfig;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.gaurav.gcallback.utils.NextFireTimeCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@Log4j2
public class JobScanner {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private SchedulerConfig schedulerConfig;

    @Autowired
    private NextFireTimeCalculator calculator;

    @Value("${scheduler.scan.batch.size:50}")
    private Integer batchSize;

    @Value("${scheduler.scan.max-batches-per-tick:20}")
    private Integer maxBatchesPerTick;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    // SCAN for jobs and create occurrence entry

    @Scheduled(fixedDelayString = "${scheduler.scan.delay-ms:5000}")
    void scanLoop() {
        for( int i=0;i < maxBatchesPerTick ;i++) {
            int processed;
            try {
                Integer n = transactionTemplate.execute(status -> scanDueJobs());
                processed = n == null? 0 : n;
            } catch (Exception ex) {
                log.error("Job scan failed; will retry next tick", ex);
                return;
            }

            if(processed < batchSize) {
                return;
            }
        }
    }

    /**
     * this func is run inside a transaction
     * @return int
     */
    private int scanDueJobs() {
        List<Job> dueJobs = jobRepository.lockDueJobs(batchSize);
        Instant now = clock.instant();
        for(Job job: dueJobs) {
            Instant fireTime = job.getNextFireTime();
            Instant next;

            try {
                Instant anchor = fireTime.isAfter(now) ? fireTime : now;
                next = calculator.nextAfter(job, anchor);

            } catch (Exception ex) {
                log.error("Cannot compute next fire time for job {}; skipping", job.getId(), ex);
                continue;
            }

            JobStatus newStatus = next == null ? JobStatus.COMPLETED : JobStatus.ACTIVE;

            int updated = jobRepository.advance(job.getId(), fireTime, next, newStatus.name());

            if(updated == 0) {
                // already advanced past
                continue;
            }

            int inserted = occurrenceRepository.insert(job.getId(), fireTime);

            if(inserted == 0 ) {
                log.warn("occurrence already exists for job {}", job.getId());
            }

            if (fireTime.isBefore(now.minusSeconds(60))) {
                log.info("Job {} misfired ", job.getId());
            }

        }
        return dueJobs.size();
    }

}
