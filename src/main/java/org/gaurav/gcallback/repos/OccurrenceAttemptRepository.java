package org.gaurav.gcallback.repos;

import org.gaurav.gcallback.dao.models.OccurrenceAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OccurrenceAttemptRepository extends JpaRepository<OccurrenceAttempt, Long> {

    @Modifying
    @Query(value = """
        UPDATE occurrence_attempts oa
        SET outcome = 'WORKER_LOST',
            finished_at = now(),
            error_message = 'worker did not report a result before timeout'
        FROM occurrences o
            JOIN jobs j ON j.id = o.job_id
        WHERE oa.occurrence_id = o.id
            AND oa.attempt_number = o.attempt_count
            AND oa.outcome = 'IN_FLIGHT'
            AND o.state = 'IN_PROGRESS'
            AND o.claimed_at < now() - ((j.timeout_ms + :slackMs)* interval '1 millisecond')
        """, nativeQuery = true)
    int markLostAttempts(@Param("slackMs") long slackMs);

    List<OccurrenceAttempt> findByOccurrenceIdOrderByAttemptNumber(Long occurrenceId);

    List<OccurrenceAttempt> findByOccurrenceIdOrderByAttemptNumberAsc(Long occurrenceId);

    Optional<OccurrenceAttempt> findTopByOccurrenceIdOrderByAttemptNumberDesc(Long occurrenceId);

    @Query("SELECT AVG(oa.latencyMs) FROM OccurrenceAttempt oa WHERE oa.latencyMs IS NOT NULL AND oa.outcome = 'SUCCESS'")
    Double getAverageLatency();

    @Query("SELECT DISTINCT oa.workerId FROM OccurrenceAttempt oa WHERE oa.workerId IS NOT NULL")
    List<String> findDistinctWorkers();
}
