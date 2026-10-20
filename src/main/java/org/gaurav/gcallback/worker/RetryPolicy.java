package org.gaurav.gcallback.worker;


import org.gaurav.gcallback.enums.OccurrenceState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryPolicy {
    public record Decision(OccurrenceState state, Instant nextAttemptAt) {}

    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryPolicy(@Value("${worker.retry.base-delay-ms:5000}") long baseDelayMs,
                       @Value("${worker.retry.max-delay-ms:600000}") long maxDelayMs) {
        this.baseDelay = Duration.ofMillis(baseDelayMs);
        this.maxDelay = Duration.ofMillis(maxDelayMs);
    }
    public Decision decide(CallResult result, int attemptCount, int maxAttempts, Instant now) {
        if(result.isSuccess()) {
            return new Decision(OccurrenceState.SUCCEEDED, null);
        }
        if(!isRetryable(result) || attemptCount >= maxAttempts) {
            return new Decision(OccurrenceState.FAILED, null);
        }

        return new Decision(OccurrenceState.RETRY_SCHEDULED, now.plus(backoff(attemptCount)));
    }
    static boolean isRetryable(CallResult r) {
        return switch (r.getOutcome()) {
            case TIMEOUT, CONNECT_ERROR, WORKER_LOST -> true;
            case HTTP_ERROR -> {
                int code = r.getStatusCode() == null ? 0 : r.getStatusCode();
                yield code >= 500 || code == 408 || code == 429;
            }
            case SUCCESS, REQUEST_ERROR, IN_FLIGHT -> false;
        };
    }

    Duration backoff(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 20);
        long millis = Math.min(baseDelay.toMillis() << exponent, maxDelay.toMillis());
        long half = millis/2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(half + 1));

    }
}
