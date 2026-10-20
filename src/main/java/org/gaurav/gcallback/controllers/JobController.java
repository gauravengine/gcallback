package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.ApiResponse;
import org.gaurav.gcallback.dto.JobRequest;
import org.gaurav.gcallback.dto.JobResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.JobStatus;
import org.gaurav.gcallback.services.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    @Autowired
    private JobService jobService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<JobResponse>>> getJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        PagedResponse<JobResponse> jobs = jobService.getJobs(status, search, page, size);
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(@RequestBody JobRequest req) {
        JobResponse created = jobService.createJob(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Job created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(@PathVariable Long id, @RequestBody JobRequest req) {
        JobResponse updated = jobService.updateJob(id, req);
        return ResponseEntity.ok(ApiResponse.ok("Job updated successfully", updated));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<JobResponse>> pauseJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Job paused successfully", jobService.pauseJob(id)));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<JobResponse>> resumeJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Job resumed successfully", jobService.resumeJob(id)));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ApiResponse<OccurrenceResponse>> triggerJob(@PathVariable Long id) {
        OccurrenceResponse occ = jobService.triggerJob(id);
        return ResponseEntity.ok(ApiResponse.ok("Ad-hoc execution queued", occ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean hard) {
        jobService.deleteJob(id, hard);
        return ResponseEntity.ok(ApiResponse.ok("Job deleted successfully", null));
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<ApiResponse<String>> seedDemo() {
        jobService.seedDemoJobs();
        return ResponseEntity.ok(ApiResponse.ok("Demo jobs seeded successfully", "Demo jobs created"));
    }
}
