package org.gaurav.gcallback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.ScheduleType;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private Long id;
    private String name;
    private ScheduleType scheduleType;
    private String cronExpression;
    private String timeZone;
    private Instant runAt;
    private String httpMethod;
    private String httpUrl;
    private Map<String, String> httpHeaders;
    private Map<String, String> httpQueryParams;
    private String httpBody;
    private int maxAttempts;
    private int timeoutMs;
    private JobStatus status;
    private Instant nextFireTime;
    private Instant lastFireTime;
    private Instant createdAt;
    private Instant updatedAt;

    // Aggregated stats
    private long totalOccurrences;
    private long successfulOccurrences;
    private long failedOccurrences;
    private String lastExecutionStatus;
}
