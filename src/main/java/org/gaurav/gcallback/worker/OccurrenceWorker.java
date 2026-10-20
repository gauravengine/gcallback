package org.gaurav.gcallback.worker;

import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.dao.models.OccurrenceAttempt;
import org.gaurav.gcallback.enums.AttemptOutcome;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceAttemptRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@Log4j2
public class OccurrenceWorker {
    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceAttemptRepository occurrenceAttemptRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private HttpCallExecutor httpCallExecutor;

    @Autowired
    private RetryPolicy retryPolicy;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier("workerId")
    private String workerId;

    private record Claim(Job job, long occurrenceId, int attemptNumber, long attemptId) {}

    @KafkaListener(topics = "${app.kafka.topics.job-dispatch}",
                    concurrency = "${worker.concurrency:3}")
    public void onMessage(ConsumerRecord<String, String> record) {
        long occurrenceId;

        try {
            occurrenceId = Long.parseLong(record.value());
        } catch(NumberFormatException ex) {
            log.error("dropping malformed message {}", record);
            return;
        }

        Optional<Claim> claimed = transactionTemplate.execute(status -> claim(occurrenceId));

        if(claimed == null || claimed.isEmpty()) {
            return;
        }

        Claim claim = claimed.get();

        CallResult result = httpCallExecutor.execute(claim.job(), occurrenceId, claim.attemptNumber);

        transactionTemplate.executeWithoutResult(status -> complete(claim, result));
    }

    private Optional<Claim> claim(long occurrenceId) {
        Occurrence occ = occurrenceRepository.findByIdForUpdate(occurrenceId).orElse(null);

        if(occ == null) {
            log.warn("Occurrence {} not found; dropping message", occurrenceId);
            return Optional.empty();
        }

        if(occ.getState() != OccurrenceState.DISPATCHED) {
            log.debug("Occurrence {} is {}, not DISPATCHED; duplicate or stale message", occurrenceId, occ.getState());
            return Optional.empty();
        }

        Job job = jobRepository.findById(occ.getJobId()).orElse(null);

        if(job == null || job.getStatus() == JobStatus.PAUSED || job.getStatus() == JobStatus.DELETED) {
            occ.setState(OccurrenceState.CANCELLED);
            occ.setNextAttemptAt(null);
            log.info("Cancelled occurrence {}: job {} is {}", occurrenceId, occ.getJobId(),
                    job == null ? "missing" : job.getStatus());
            return Optional.empty();
        }

        Instant now = clock.instant();
        int attemptNumber = occ.getAttemptCount() + 1;
        occ.setState(OccurrenceState.IN_PROGRESS);
        occ.setAttemptCount(attemptNumber);
        occ.setClaimedBy(workerId);
        occ.setClaimedAt(now);
        occ.setNextAttemptAt(null);

        OccurrenceAttempt attempt = new OccurrenceAttempt();
        attempt.setOccurrenceId(occurrenceId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStartedAt(now);
        attempt.setOutcome(AttemptOutcome.IN_FLIGHT);
        attempt.setWorkerId(workerId);
        attempt = occurrenceAttemptRepository.save(attempt);

        return Optional.of(new Claim(job, occurrenceId, attemptNumber, attempt.getId()));
    }
    private void complete(final Claim claim, final CallResult result) {
        Instant now = clock.instant();

        OccurrenceAttempt attempt = occurrenceAttemptRepository.findById(claim.attemptId).orElse(null);

        if(attempt == null || attempt.getOutcome() != AttemptOutcome.IN_FLIGHT) {
            log.warn("Attempt {} of occurrence {} was already closed (reaper?); discarding result {}",
                    claim.attemptNumber, claim.occurrenceId, result.getOutcome());
            return;
        }

        attempt.setFinishedAt(now);
        attempt.setOutcome(result.getOutcome());
        attempt.setStatusCode(result.getStatusCode());
        attempt.setLatencyMs(result.getLatencyMs());
        attempt.setResponseBody(result.getResponseBody());
        attempt.setErrorMessage(result.getErrorMessage());

        occurrenceAttemptRepository.flush();

        Occurrence occ = occurrenceRepository.findByIdForUpdate(claim.occurrenceId).orElse(null);
        if(occ == null
            || occ.getState() != OccurrenceState.IN_PROGRESS
            || !workerId.equals(occ.getClaimedBy())
            || occ.getAttemptCount() != claim.attemptNumber) {

            log.warn("Occurrence {} no longer owned by attempt; not updating state", claim.occurrenceId);
            return;
        }

        RetryPolicy.Decision decision =
                retryPolicy.decide(result, claim.attemptNumber, claim.job.getMaxAttempts(), now);

        occ.setState(decision.state());
        occ.setNextAttemptAt(decision.nextAttemptAt());
        occ.setClaimedBy(null);

        log.info("Occurrence {} attempt {} -> {} ({}{})", claim.occurrenceId, claim.attemptNumber,
                decision.state(), result.getOutcome(),
                result.getStatusCode() == null ? "" : " " + result.getStatusCode()
        );
    }

}
