package dev.vality.disputes.schedule;

import dev.vality.disputes.config.SchedulingConfig;
import dev.vality.disputes.service.DocumentRegistrationService;
import dev.vality.disputes.util.RemoteErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "service.candlekeep.registration.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentRegistrationTask {

    private final DocumentRegistrationService documentRegistrationService;

    @Scheduled(scheduler = SchedulingConfig.DOCUMENT_REGISTRATION_SCHEDULER,
            fixedDelayString = "${service.candlekeep.registration.fixed-delay:300000}",
            initialDelayString = "${service.candlekeep.registration.initial-delay:5000}")
    public void registerDocuments() {
        try {
            for (var fileId : documentRegistrationService.findPending()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    documentRegistrationService.registerDocument(fileId);
                } catch (Exception error) {
                    if (RemoteErrors.containsInterruption(error)) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    log.error("Failed to process document registration task, fileId={}", fileId, error);
                }
            }
        } catch (Exception error) {
            log.error("Document registration job failed", error);
        }
    }
}
