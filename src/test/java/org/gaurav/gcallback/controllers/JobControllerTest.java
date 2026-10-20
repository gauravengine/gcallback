package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.JobRequest;
import org.gaurav.gcallback.dto.JobResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.enums.ScheduleType;
import org.gaurav.gcallback.services.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobControllerTest {

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    @Test
    void testGetJobs() {
        JobResponse job = JobResponse.builder()
                .id(1L)
                .name("Stripe Webhook")
                .status(JobStatus.ACTIVE)
                .scheduleType(ScheduleType.CRON)
                .httpUrl("https://httpbin.org/post")
                .build();

        PagedResponse<JobResponse> paged = PagedResponse.of(List.of(job), 0, 15, 1);
        when(jobService.getJobs(any(), any(), anyInt(), anyInt())).thenReturn(paged);

        var response = jobController.getJobs(null, null, 0, 15);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().getItems().size());
    }

    @Test
    void testCreateJob() {
        JobRequest req = JobRequest.builder()
                .name("Test Callback")
                .scheduleType(ScheduleType.CRON)
                .cronExpression("0 */5 * * * *")
                .httpUrl("https://httpbin.org/post")
                .httpMethod("POST")
                .build();

        JobResponse res = JobResponse.builder()
                .id(10L)
                .name("Test Callback")
                .status(JobStatus.ACTIVE)
                .build();

        when(jobService.createJob(any(JobRequest.class))).thenReturn(res);

        var response = jobController.createJob(req);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().getData().getId());
    }

    @Test
    void testTriggerJob() {
        OccurrenceResponse occ = OccurrenceResponse.builder()
                .id(100L)
                .jobId(10L)
                .state(OccurrenceState.PENDING)
                .scheduledFor(Instant.now())
                .build();

        when(jobService.triggerJob(10L)).thenReturn(occ);

        var response = jobController.triggerJob(10L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getData().getId());
        assertEquals(OccurrenceState.PENDING, response.getBody().getData().getState());
    }

    @Test
    void testPauseAndResumeJob() {
        JobResponse pausedJob = JobResponse.builder().id(10L).status(JobStatus.PAUSED).build();
        JobResponse activeJob = JobResponse.builder().id(10L).status(JobStatus.ACTIVE).build();

        when(jobService.pauseJob(10L)).thenReturn(pausedJob);
        when(jobService.resumeJob(10L)).thenReturn(activeJob);

        var pauseRes = jobController.pauseJob(10L);
        assertEquals(JobStatus.PAUSED, pauseRes.getBody().getData().getStatus());

        var resumeRes = jobController.resumeJob(10L);
        assertEquals(JobStatus.ACTIVE, resumeRes.getBody().getData().getStatus());
    }
}
