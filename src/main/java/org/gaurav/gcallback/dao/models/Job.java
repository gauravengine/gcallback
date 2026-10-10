package org.gaurav.gcallback.dao.models;

import jakarta.persistence.*;
import lombok.Data;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.ScheduleType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "jobs")
@Data
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(nullable = false)
    private String timeZone;

    @Column(name = "run_at")
    private Instant runAt;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(nullable = false, name = "http_url")
    private String httpUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "http_headers", nullable = false)
    private Map<String, String> httpHeaders = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "http_query_params", nullable = false)
    private Map<String, String> httpQueryParams = new HashMap<>();

    @Column(name = "http_body")
    private String httpBody;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private JobStatus status = JobStatus.ACTIVE;

    @Column(name = "next_fire_time")
    private Instant nextFireTime;

    @Column(name = "last_fire_time")
    private Instant lastFireTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
