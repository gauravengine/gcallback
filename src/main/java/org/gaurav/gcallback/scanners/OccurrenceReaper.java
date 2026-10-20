package org.gaurav.gcallback.scanners;


import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.repos.OccurrenceAttemptRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Log4j2
public class OccurrenceReaper {

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceAttemptRepository occurrenceAttemptRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${scheduler.reaper.dispatched-stale-seconds:300}")
    private long dispatchedStaleSeconds;

    @Value("${scheduler.reaper.in-progress-slack-ms:60000}")
    private long inProgressSlackMs;


    @Scheduled(fixedDelayString = "${scheduler.reaper.delay-ms:30000}")
    void reap() {
        try {
            Integer redispatched = transactionTemplate.execute(status ->
                    occurrenceRepository.resetStaleDispatched(dispatchedStaleSeconds));

            Integer recovered = transactionTemplate.execute(status -> {
               occurrenceAttemptRepository.markLostAttempts(inProgressSlackMs);
                return occurrenceRepository.recoverLostInProgress(inProgressSlackMs);
            });

            if(nonZero(redispatched) || nonZero(recovered)) {
                log.warn("Reaper: {} stale DISPATCHED reset, {} lost IN_PROGRESS recovered", redispatched, recovered);
            }
        } catch (Exception ex) {
            log.error("Reaper run failed", ex);
        }

    }

    private static boolean nonZero(Integer n) {
        return n!=null && n>0;
    }
}
