package org.gaurav.gcallback.dao.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.AttemptOutcome;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "occurrence_attempts")
@Data
public class OccurrenceAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "occurrence_id", nullable = false)
    private Long occurrenceId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptOutcome outcome;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "worker_id")
    private String workerId;

    @Data
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long id;
        private Instant startedAt;
    }


}
