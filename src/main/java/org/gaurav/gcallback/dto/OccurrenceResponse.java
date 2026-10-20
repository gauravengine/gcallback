package org.gaurav.gcallback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.AttemptOutcome;
import org.gaurav.gcallback.enums.OccurrenceState;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccurrenceResponse {
    private Long id;
    private Long jobId;
    private String jobName;
    private String httpMethod;
    private String httpUrl;
    private Instant scheduledFor;
    private OccurrenceState state;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;
    private String claimedBy;
    private Instant claimedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Latest attempt details if available
    private Integer lastStatusCode;
    private Integer lastLatencyMs;
    private AttemptOutcome lastOutcome;
    private String lastErrorMessage;

    // Full attempt history
    private List<OccurrenceAttemptResponse> attempts;
}
