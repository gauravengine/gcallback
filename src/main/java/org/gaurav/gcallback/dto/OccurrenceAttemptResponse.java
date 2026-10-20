package org.gaurav.gcallback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.AttemptOutcome;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccurrenceAttemptResponse {
    private Long id;
    private Long occurrenceId;
    private Integer attemptNumber;
    private Instant startedAt;
    private Instant finishedAt;
    private AttemptOutcome outcome;
    private Integer statusCode;
    private Integer latencyMs;
    private String responseBody;
    private String errorMessage;
    private String workerId;
}
