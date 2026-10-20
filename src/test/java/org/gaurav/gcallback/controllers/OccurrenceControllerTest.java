package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.OccurrenceAttemptResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.AttemptOutcome;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.services.OccurrenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OccurrenceControllerTest {

    @Mock
    private OccurrenceService occurrenceService;

    @InjectMocks
    private OccurrenceController occurrenceController;

    @Test
    void testGetOccurrences() {
        OccurrenceResponse occ = OccurrenceResponse.builder()
                .id(1L)
                .jobId(5L)
                .state(OccurrenceState.SUCCEEDED)
                .scheduledFor(Instant.now())
                .build();

        PagedResponse<OccurrenceResponse> paged = PagedResponse.of(List.of(occ), 0, 15, 1);
        when(occurrenceService.getOccurrences(any(), any(), anyInt(), anyInt())).thenReturn(paged);

        var res = occurrenceController.getOccurrences(null, null, 0, 15);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().getData().getItems().size());
    }

    @Test
    void testRetryOccurrence() {
        OccurrenceResponse res = OccurrenceResponse.builder()
                .id(1L)
                .state(OccurrenceState.PENDING)
                .build();

        when(occurrenceService.retryOccurrence(1L)).thenReturn(res);

        var response = occurrenceController.retryOccurrence(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(OccurrenceState.PENDING, response.getBody().getData().getState());
    }

    @Test
    void testGetAttempts() {
        OccurrenceAttemptResponse attempt = OccurrenceAttemptResponse.builder()
                .id(101L)
                .occurrenceId(1L)
                .attemptNumber(1)
                .outcome(AttemptOutcome.SUCCESS)
                .statusCode(200)
                .latencyMs(120)
                .build();

        when(occurrenceService.getAttempts(1L)).thenReturn(List.of(attempt));

        var response = occurrenceController.getAttempts(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(200, response.getBody().getData().get(0).getStatusCode());
    }
}
