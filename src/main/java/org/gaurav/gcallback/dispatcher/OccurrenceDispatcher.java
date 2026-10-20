package org.gaurav.gcallback.dispatcher;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.dao.models.Occurrence;
import org.gaurav.gcallback.enums.OccurrenceState;
import org.gaurav.gcallback.repos.OccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Log4j2
public class OccurrenceDispatcher {

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${scheduler.dispatcher.batch-size:100}")
    private int batchSize;

    @Value("${scheduler.dispatcher.max-batches-per-tick:20}")
    private int maxBatchesPerTick;

    @Value("${scheduler.dispatcher.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    @Value("${app.kafka.topics.job-dispatch}")
    private String topic;

    private record DispatchItem(Long occurrenceId, Long jobId, OccurrenceState previousState) {}

    @Scheduled(fixedDelayString = "${scheduler.dispatch.delay-ms:1000}")
    void dispatch() {
        for(int i=0; i< maxBatchesPerTick; i++) {
            List<DispatchItem> items;

            try {
                items = transactionTemplate.execute(status -> claimBatch());
            } catch (Exception ex) {
                log.error("Dispatch claim failed; will retry next tick", ex);
                return;
            }

            if(items == null || items.isEmpty()) {
                return;
            }

            publish(items);
            if(items.size() < batchSize) {
                return;
            }
        }
    }

    private List<DispatchItem> claimBatch() {
        List<Occurrence> due = occurrenceRepository.lockDue(batchSize);

        List<DispatchItem> items = new ArrayList<>(due.size());

        for(Occurrence occurrence : due) {
            items.add(new DispatchItem(occurrence.getId(), occurrence.getJobId(), occurrence.getState()));
            occurrence.setState(OccurrenceState.DISPATCHED);
        }

        return items;
    }
    private void publish(final List<DispatchItem> items) {
        List<CompletableFuture<SendResult<String, String>> > futures = new ArrayList<>(items.size());

        for(DispatchItem dispatchItem : items) {
            futures.add(kafkaTemplate.send(topic,
                    String.valueOf(dispatchItem.jobId),
                    String.valueOf(dispatchItem.occurrenceId)));

        }

        for(int i = 0; i< items.size(); i++) {
            DispatchItem dispatchItem = items.get(i);
            try {
                futures.get(i).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                revert(dispatchItem);
            } catch (Exception ex) {
                log.warn("Publishing failed for occurrence {}, reverting to {}",
                        dispatchItem.occurrenceId, dispatchItem.previousState, ex);
                revert(dispatchItem);
            }
        }
    }

    private void revert(final DispatchItem item) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                occurrenceRepository.findByIdForUpdate(item.occurrenceId()).ifPresent(occurrence -> {
                    if(occurrence.getState() == OccurrenceState.DISPATCHED) {
                        occurrence.setState(item.previousState);
                    }
                });
            });
        } catch (Exception ex) {
            log.error("COULD NOT REVERT THE OCCURRENCE STATE OF OCCURRENCE {}, WILL BE REVERTED BY THE REAPER", item.occurrenceId, ex);
        }
    }
}
