package org.gaurav.gcallback.dao.models;

import jakarta.persistence.*;
import lombok.Data;
import org.gaurav.gcallback.enums.OccurrenceState;

import java.time.Instant;

@Entity
@Data
@Table(name = "occurrences")
public class Occurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private OccurrenceState state;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate()
    {
        updatedAt = Instant.now();
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
