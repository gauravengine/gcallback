package org.gaurav.gcallback.repos;

import jakarta.persistence.LockModeType;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.enums.JobStatus;
import org.hibernate.LockMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    /*
        function to find jobs that are due now to run runs inside a transaction otherwise the for UPDATE lock is given up when transaction completes
        which is the auto case when this SELECt query will return
     */

    @Query(
        value = """
            SELECT * FROM jobs\s
            WHERE next_fire_time IS NOT NULL\s
                    AND next_fire_time <= now()\s
                    AND status = 'ACTIVE'
            ORDER BY next_fire_time
            LIMIT :limit       \s
            FOR UPDATE SKIP LOCKED
           \s"""
            , nativeQuery = true
    )
    List<Job> lockDueJobs(@Param("limit") int limit);

    /*
        advance a job to its next fire time, next_fire_time = :current is a second line of defense after above FOR UPDATE lock
     */
    @Modifying
    @Query(
            value = """
                UPDATE jobs
                SET next_fire_time  = :next,
                    last_fire_time = :current,
                    status = :status,
                    updated_at = now()
                WHERE id = :id AND next_fire_time = :current
            """, nativeQuery = true
    )
    int advance(@Param("id") Long id,
                @Param("current") Instant current,
                @Param("next") Instant next,
                @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j where j.id = :id ")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByStatusNot(JobStatus status, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.status != :excludeStatus AND (LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(j.httpUrl) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Job> searchJobs(@Param("search") String search, @Param("excludeStatus") JobStatus excludeStatus, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.status = :status AND (LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(j.httpUrl) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Job> searchJobsWithStatus(@Param("search") String search, @Param("status") JobStatus status, Pageable pageable);

    long countByStatus(JobStatus status);

    long countByStatusNot(JobStatus status);
}
