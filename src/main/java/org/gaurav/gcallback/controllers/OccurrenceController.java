package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.ApiResponse;
import org.gaurav.gcallback.dto.OccurrenceAttemptResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.services.OccurrenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/occurrences")
@CrossOrigin(origins = "*")
public class OccurrenceController {

    @Autowired
    private OccurrenceService occurrenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OccurrenceResponse>>> getOccurrences(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) OccurrenceState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        PagedResponse<OccurrenceResponse> occurrences = occurrenceService.getOccurrences(jobId, state, page, size);
        return ResponseEntity.ok(ApiResponse.ok(occurrences));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OccurrenceResponse>> getOccurrence(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(occurrenceService.getOccurrence(id)));
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<ApiResponse<List<OccurrenceAttemptResponse>>> getAttempts(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(occurrenceService.getAttempts(id)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<OccurrenceResponse>> retryOccurrence(@PathVariable Long id) {
        OccurrenceResponse res = occurrenceService.retryOccurrence(id);
        return ResponseEntity.ok(ApiResponse.ok("Occurrence requeued for execution", res));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OccurrenceResponse>> cancelOccurrence(@PathVariable Long id) {
        OccurrenceResponse res = occurrenceService.cancelOccurrence(id);
        return ResponseEntity.ok(ApiResponse.ok("Occurrence cancelled", res));
    }
}
