package org.gaurav.gcallback.scanners;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.config.SchedulerConfig;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.gaurav.gcallback.utils.NextFireTimeCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Value("${scan.batch.size:50}")
    private Integer batchSize;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // SCAN for jobs and create occurrence entry

    @Scheduled(fixedDelayString = "${scheduler.scan.delay-ms:5000}")
    void scanLoop() {
        List<Job> potentialJobs = jobRepository.findDueJobs(batchSize);

        for(Job job : potentialJobs) {
            try {
                transactionTemplate.executeWithoutResult((status) -> {
                    claim(job);
                    log.info("STATUS OF STATUS :p {} ", status.getTransactionName());
                });
            } catch (Exception ex) {
                log.error(ex);
            }
        }
    }

    private void claim(final Job job) {
        Instant fireTime = job.getNextFireTime();
        Instant next = calculator.nextAfter(job, fireTime);

        int row = jobRepository.advance(job.getId(), fireTime, next);
        if( row ==0) {
            log.info("already forwarded");
            return;
        }
        occurrenceRepository.insert(job.getId(), fireTime);
    }

}
