package org.gaurav.gcallback.repos;

import org.gaurav.gcallback.dao.models.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    /*
        function to find jobs that are due now to run
     */

    @Query(
        value = """
            SELECT * FROM jobs\s
            WHERE next_fire_time IS NOT NULL\s
                    AND next_fire_time <= now()\s
                    AND status = 'ACTIVE'
                    ORDER BY next_fire_time
                LIMIT :limit       \s
           \s"""
            , nativeQuery = true
    )
    List<Job> findDueJobs(@Param("limit") int limit);

    /*
        advance a job to its next fire time
     */
    @Modifying
    @Query(
            value = """
                UPDATE jobs
                SET next_fire_time  = :next,
                                last_fire_time = :current,
                                            updated_at = now()
                WHERE id = :id AND next_fire_time = :current
            """, nativeQuery = true
    )
    int advance(@Param("id") Long id,
                @Param("current") Instant current,
                @Param("next") Instant next);
}
