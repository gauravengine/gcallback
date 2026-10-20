package org.gaurav.gcallback.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.AttemptOutcome;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallResult {
    private AttemptOutcome outcome;
    private Integer statusCode;
    private int latencyMs;
    private String responseBody;
    private String errorMessage;

    public boolean isSuccess() {
        return outcome == AttemptOutcome.SUCCESS;
    }
}
