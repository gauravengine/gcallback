package org.gaurav.gcallback.repos;

import org.gaurav.gcallback.dao.models.Occurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {
     // build the occurrence of a job

    @Modifying
    @Query(value = """
            INSERT INTO occurrences (job_id, scheduled_for, state, created_at, updated_at)
                    VALUES(:jobId, :scheduledFor, 'PENDING', now(), now())
                    ON CONFLICT (job_id, scheduled_for) DO NOTHING""",
            nativeQuery = true)
    int insert(@Param("jobId") Long jobId,
               @Param("scheduledFor") Instant scheduledFor);
}
