package org.gaurav.gcallback.repos;

import jakarta.persistence.LockModeType;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.service.annotation.PatchExchange;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {
     // build the occurrence of a job

    @Modifying
    @Query(value = """
            INSERT INTO occurrences (job_id, scheduled_for, state, next_attempt_at,  created_at, updated_at)
                    VALUES(:jobId, :scheduledFor, 'PENDING', :scheduledFor, now(), now())
                    ON CONFLICT (job_id, scheduled_for) DO NOTHING""",
            nativeQuery = true)
    int insert(@Param("jobId") Long jobId,
               @Param("scheduledFor") Instant scheduledFor);


    @Query(value = """
            SELECT * FROM occurrences
                    WHERE state IN ('PENDING', 'RETRY_SCHEDULED')
                    AND next_attempt_at <= now()
                    ORDER BY next_attempt_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Occurrence> lockDue(@Param("limit") int limit);

    @Query(value = """
        SELECT * FROM occurrences
            WHERE state = 'RETRY_SCHEDULED'
            AND next_attempt_at <= now()
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<Occurrence> lockRetryDues(@Param("limit") final int limit);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o from Occurrence o where o.id = :id")
    Optional<Occurrence> findByIdForUpdate(@Param("id") final Long id);

    @Modifying
    @Query(value = """
        UPDATE occurrences
        SET state = 'PENDING',
            updated_at = now(),
                    next_attempt_at = COALESCE(next_attempt_at, now())
        WHERE state = 'DISPATCHED'
        AND updated_at < now() - (:staleSeconds * interval '1 second')
        """, nativeQuery = true)
    int resetStaleDispatched(@Param("staleSeconds") long staleSeconds);

    @Modifying
    @Query(value = """
        UPDATE occurrences o
            SET
            state = CASE When o.attempt_count >= j.max_attempts THEN 'FAILED' ELSE 'RETRY_SCHEDULED' END,
            next_attempt_at = CASE WHEN o.attempt_count >= j.max_attempts THEN NULL ELSE now() END,
            claimed_by = NULL,
            claimed_at = now()
        FROM Jobs j
        WHERE o.job_id = j.id
            AND o.state = 'IN_PROGRESS'
            AND o.claimed_at < now() - ((j.timeout_ms + :slackMs) * interval '1 millisecond')
    """, nativeQuery = true)
    int recoverLostInProgress(@Param("slackMs") final long slackMs);

    Page<Occurrence> findByJobIdOrderByScheduledForDesc(Long jobId, Pageable pageable);

    Page<Occurrence> findByJobIdAndStateOrderByScheduledForDesc(Long jobId, OccurrenceState state, Pageable pageable);

    Page<Occurrence> findAllByOrderByScheduledForDesc(Pageable pageable);

    Page<Occurrence> findByStateOrderByScheduledForDesc(OccurrenceState state, Pageable pageable);

    List<Occurrence> findTop10ByOrderByScheduledForDesc();

    long countByState(OccurrenceState state);

    long countByJobId(Long jobId);

    long countByJobIdAndState(Long jobId, OccurrenceState state);

    List<Occurrence> findByJobId(Long jobId);

    List<Occurrence> findByJobIdOrderByScheduledForDesc(Long jobId);

    @Query("SELECT o FROM Occurrence o WHERE o.scheduledFor >= :since ORDER BY o.scheduledFor ASC")
    List<Occurrence> findOccurrencesSince(@Param("since") Instant since);
}
