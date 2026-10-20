package org.gaurav.gcallback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gaurav.gcallback.enums.ScheduleType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRequest {
    private String name;
    private ScheduleType scheduleType;
    private String cronExpression;
    @Builder.Default
    private String timeZone = "UTC";
    private Instant runAt;
    @Builder.Default
    private String httpMethod = "POST";
    private String httpUrl;
    @Builder.Default
    private Map<String, String> httpHeaders = new HashMap<>();
    @Builder.Default
    private Map<String, String> httpQueryParams = new HashMap<>();
    private String httpBody;
    @Builder.Default
    private int maxAttempts = 3;
    @Builder.Default
    private int timeoutMs = 5000;
}
