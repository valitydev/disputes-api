package dev.vality.disputes.retry;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.remote.RemoteCallBudget;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundProcessing {

    private final ScheduledRetryService scheduledRetryService;
    private final RemoteCallBudget remoteCallBudget;

    public UUID processDispute(Dispute selected, Runnable operation) {
        process(selected.getId(), operation,
                () -> scheduledRetryService.deferDispute(selected.getId(), selected.getStatus()));
        return selected.getId();
    }

    public UUID processProviderCallback(ProviderCallback selected, Runnable operation) {
        process(selected.getId(), operation, () -> scheduledRetryService.deferProviderCallback(selected.getId()));
        return selected.getId();
    }

    @SneakyThrows
    private void process(UUID id, Runnable operation, Runnable retry) {
        var started = System.nanoTime();
        try {
            remoteCallBudget.runInBackground(operation);
        } catch (Throwable error) {
            if (TransientErrorClassifier.containsInterruption(error)) {
                Thread.currentThread().interrupt();
                throw error;
            }
            if (Thread.currentThread().isInterrupted() || !TransientErrorClassifier.isTransient(error)) {
                throw error;
            }
            // The transactional worker was invoked through its proxy and has already released its row lock.
            log.warn("Transient external failure for queued record {}, error={}, elapsed={}",
                    id, error.getClass().getSimpleName(), Duration.ofNanos(System.nanoTime() - started));
            try {
                retry.run();
            } catch (RuntimeException retryError) {
                retryError.addSuppressed(error);
                log.error("Failed to persist retry for queued record {}", id, retryError);
                throw retryError;
            }
        }
    }
}
