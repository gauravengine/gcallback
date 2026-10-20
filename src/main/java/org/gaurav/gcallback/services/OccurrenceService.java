package org.gaurav.gcallback.services;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.dao.models.Job;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.dao.models.OccurrenceAttempt;
import org.gaurav.gcallback.dto.OccurrenceAttemptResponse;
import org.gaurav.gcallback.dto.OccurrenceResponse;
import org.gaurav.gcallback.dto.PagedResponse;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.repos.JobRepository;
import org.gaurav.gcallback.repos.OccurrenceAttemptRepository;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Log4j2
public class OccurrenceService {

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceAttemptRepository occurrenceAttemptRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private Clock clock;

    public PagedResponse<OccurrenceResponse> getOccurrences(Long jobId, OccurrenceState state, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Occurrence> occPage;

        if (jobId != null && state != null) {
            occPage = occurrenceRepository.findByJobIdAndStateOrderByScheduledForDesc(jobId, state, pageable);
        } else if (jobId != null) {
            occPage = occurrenceRepository.findByJobIdOrderByScheduledForDesc(jobId, pageable);
        } else if (state != null) {
            occPage = occurrenceRepository.findByStateOrderByScheduledForDesc(state, pageable);
        } else {
            occPage = occurrenceRepository.findAllByOrderByScheduledForDesc(pageable);
        }

        List<OccurrenceResponse> responses = occPage.getContent().stream()
                .map(occ -> toOccurrenceResponse(occ, false))
                .toList();

        return PagedResponse.of(responses, occPage.getNumber(), occPage.getSize(), occPage.getTotalElements());
    }

    public OccurrenceResponse getOccurrence(Long id) {
        Occurrence occurrence = occurrenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Occurrence not found with id: " + id));
        return toOccurrenceResponse(occurrence, true);
    }

    public List<OccurrenceAttemptResponse> getAttempts(Long occurrenceId) {
        if (!occurrenceRepository.existsById(occurrenceId)) {
            throw new NoSuchElementException("Occurrence not found with id: " + occurrenceId);
        }
        return occurrenceAttemptRepository.findByOccurrenceIdOrderByAttemptNumberAsc(occurrenceId).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    @Transactional
    public OccurrenceResponse retryOccurrence(Long id) {
        Occurrence occurrence = occurrenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Occurrence not found with id: " + id));

        Instant now = clock.instant();
        occurrence.setState(OccurrenceState.PENDING);
        occurrence.setNextAttemptAt(now);
        occurrence.setClaimedBy(null);
        occurrence.setClaimedAt(null);

        occurrence = occurrenceRepository.save(occurrence);
        log.info("Manually requeued occurrence {}: set to PENDING for immediate dispatch", id);

        return toOccurrenceResponse(occurrence, true);
    }

    @Transactional
    public OccurrenceResponse cancelOccurrence(Long id) {
        Occurrence occurrence = occurrenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Occurrence not found with id: " + id));

        occurrence.setState(OccurrenceState.CANCELLED);
        occurrence.setNextAttemptAt(null);
        occurrence.setClaimedBy(null);

        occurrence = occurrenceRepository.save(occurrence);
        log.info("Cancelled occurrence {}", id);

        return toOccurrenceResponse(occurrence, true);
    }

    public OccurrenceResponse toOccurrenceResponse(Occurrence occ, boolean includeAttempts) {
        Job job = jobRepository.findById(occ.getJobId()).orElse(null);

        OccurrenceResponse.OccurrenceResponseBuilder builder = OccurrenceResponse.builder()
                .id(occ.getId())
                .jobId(occ.getJobId())
                .jobName(job != null ? job.getName() : "Unknown Job #" + occ.getJobId())
                .httpMethod(job != null ? job.getHttpMethod() : "POST")
                .httpUrl(job != null ? job.getHttpUrl() : "")
                .scheduledFor(occ.getScheduledFor())
                .state(occ.getState())
                .attemptCount(occ.getAttemptCount())
                .maxAttempts(job != null ? job.getMaxAttempts() : 3)
                .nextAttemptAt(occ.getNextAttemptAt())
                .claimedBy(occ.getClaimedBy())
                .claimedAt(occ.getClaimedAt())
                .createdAt(occ.getCreatedAt())
                .updatedAt(occ.getUpdatedAt());

        Optional<OccurrenceAttempt> latestAttempt = occurrenceAttemptRepository.findTopByOccurrenceIdOrderByAttemptNumberDesc(occ.getId());
        if (latestAttempt.isPresent()) {
            OccurrenceAttempt att = latestAttempt.get();
            builder.lastStatusCode(att.getStatusCode())
                    .lastLatencyMs(att.getLatencyMs())
                    .lastOutcome(att.getOutcome())
                    .lastErrorMessage(att.getErrorMessage());
        }

        if (includeAttempts) {
            List<OccurrenceAttemptResponse> attempts = occurrenceAttemptRepository.findByOccurrenceIdOrderByAttemptNumberAsc(occ.getId())
                    .stream()
                    .map(this::toAttemptResponse)
                    .toList();
            builder.attempts(attempts);
        }

        return builder.build();
    }

    public OccurrenceAttemptResponse toAttemptResponse(OccurrenceAttempt att) {
        return OccurrenceAttemptResponse.builder()
                .id(att.getId())
                .occurrenceId(att.getOccurrenceId())
                .attemptNumber(att.getAttemptNumber())
                .startedAt(att.getStartedAt())
                .finishedAt(att.getFinishedAt())
                .outcome(att.getOutcome())
                .statusCode(att.getStatusCode())
                .latencyMs(att.getLatencyMs())
                .responseBody(att.getResponseBody())
                .errorMessage(att.getErrorMessage())
                .workerId(att.getWorkerId())
                .build();
    }
}
