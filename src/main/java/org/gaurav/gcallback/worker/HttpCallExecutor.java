package org.gaurav.gcallback.worker;

import lombok.extern.log4j.Log4j2;
import org.aspectj.weaver.ast.Call;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.enums.AttemptOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

@Component
@Log4j2
public class HttpCallExecutor {

    @Autowired
    private HttpClient httpClient;

    @Value("${worker.http.max-response-body-bytes:4096}")
    private int maxResponseBodyBytes;

    public CallResult execute(Job job, long occurrenceId, int attemptNumber) {
        HttpRequest httpRequest;

        try {
            httpRequest = buildRequest(job, occurrenceId, attemptNumber);
        } catch(IllegalArgumentException ex) {
            return CallResult.builder()
                    .outcome(AttemptOutcome.REQUEST_ERROR)
                    .statusCode(null)
                    .latencyMs(0)
                    .responseBody(null)
                    .errorMessage(ex.getMessage())
                    .build();
        }

        long start = System.nanoTime();

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream in = response.body()) {
                body = new String(in.readNBytes(maxResponseBodyBytes), StandardCharsets.UTF_8);
            }
            int code = response.statusCode();
            AttemptOutcome outcome = (code >= 200 && code <300) ? AttemptOutcome.SUCCESS : AttemptOutcome.HTTP_ERROR;
            return CallResult.builder()
                    .outcome(outcome)
                    .statusCode(code)
                    .latencyMs(elapsedMs(start))
                    .responseBody(body)
                    .build();
        } catch (HttpTimeoutException ex) {
            return CallResult.builder().outcome(AttemptOutcome.TIMEOUT).latencyMs(elapsedMs(start)).errorMessage(ex.toString()).build();
        } catch(IOException ex) {
            return CallResult.builder().outcome(AttemptOutcome.CONNECT_ERROR).latencyMs(elapsedMs(start)).errorMessage(ex.toString()).build();
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CallResult.builder().errorMessage("INTERRUPTED").outcome(AttemptOutcome.CONNECT_ERROR).latencyMs(elapsedMs(start)).build();
        }
    }

    private HttpRequest buildRequest(Job job, long occurrenceId, int attemptNumber) {
        String method = job.getHttpMethod().toUpperCase();
        String body = job.getHttpBody();
        HttpRequest.BodyPublisher publisher = (body == null || body.isEmpty())
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildUri(job.getHttpUrl(), job.getHttpQueryParams()))
                .timeout(Duration.ofMillis(job.getTimeoutMs()))
                .method(method, publisher);

        if(job.getHttpHeaders() != null) {
            job.getHttpHeaders().forEach(builder::header);
        }

        builder.header("Idempotency-Key", "gcallback-occ-" + occurrenceId);
        builder.header("X-GCallback-Attempt", String.valueOf(attemptNumber));

        return builder.build();
    }
    private static URI buildUri(String url, Map<String, String> queryParams) {
        if(queryParams == null || queryParams.isEmpty()) {
            return URI.create(url);
        }

        StringJoiner query = new StringJoiner("&");
        queryParams.forEach((k,v) ->
            query.add(
                    URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" +
                    URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8))

        );
       String separator = url.contains("?") ? "&" : "?";

       return URI.create(url + separator + query);
    }
    private static int elapsedMs(long startNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
