package dev.vality.disputes.retry;

import dev.vality.disputes.config.properties.BackgroundProcessingProperties;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.service.DisputesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static dev.vality.disputes.polling.TimeOptionsExtractors.DEFAULT_INITIAL_EXPONENTIAL_SEC;
import static dev.vality.disputes.polling.TimeOptionsExtractors.MAX_TIME_BACKOFF_SEC;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledRetryService {

    private final DisputeDao disputeDao;
    private final ProviderCallbackDao providerCallbackDao;
    private final ExponentialBackOffPollingServiceWrapper pollingService;
    private final BackgroundProcessingProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferDispute(UUID id, DisputeStatus expectedStatus) {
        try {
            var current = disputeDao.getSkipLocked(id);
            var now = LocalDateTime.now(ZoneOffset.UTC);
            if (current.getStatus() != expectedStatus
                    || !DisputesService.DISPUTE_PENDING_STATUSES.contains(current.getStatus())
                    || current.getNextCheckAfter().isAfter(now)) {
                log.debug("Skip retry of changed or already deferred Dispute {}", id);
                return;
            }
            var next = pollingService.prepareTransportRetry(current.getCreatedAt(), now, retryOptions());
            if (disputeDao.deferNextCheckAfter(id, expectedStatus, current.getNextCheckAfter(), next) == 1) {
                log.info("Retry Dispute {}, invoice={}, payment={}, next_check_after={} UTC",
                        id, current.getInvoiceId(), current.getPaymentId(), next);
            }
        } catch (NotFoundException ex) {
            if (ex.getType() != NotFoundException.Type.DISPUTE) {
                throw ex;
            }
            log.debug("Skip retry of locked or missing Dispute {}", id);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferProviderCallback(UUID id) {
        try {
            var current = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(id);
            var now = LocalDateTime.now(ZoneOffset.UTC);
            if (current.getStatus() != ProviderPaymentsStatus.create_adjustment
                    || current.getNextCheckAfter().isAfter(now)) {
                log.debug("Skip retry of changed or already deferred ProviderCallback {}", id);
                return;
            }
            var next = pollingService.prepareTransportRetry(current.getCreatedAt(), now, retryOptions());
            var updated = providerCallbackDao.deferNextCheckAfter(id, current.getStatus(),
                    current.getNextCheckAfter(), next);
            if (updated == 1) {
                log.info("Retry ProviderCallback {}, invoice={}, payment={}, next_check_after={} UTC",
                        id, current.getInvoiceId(), current.getPaymentId(), next);
            }
        } catch (NotFoundException ex) {
            if (ex.getType() != NotFoundException.Type.PROVIDERCALLBACK) {
                throw ex;
            }
            log.debug("Skip retry of locked or missing ProviderCallback {}", id);
        }
    }

    private Map<String, String> retryOptions() {
        // Reuse the existing age-based exponential backoff; no new attempt counters or database migration.
        return Map.of(DEFAULT_INITIAL_EXPONENTIAL_SEC,
                Long.toString(properties.getRetry().getInitialDelay().toSeconds()),
                MAX_TIME_BACKOFF_SEC, Long.toString(properties.getRetry().getMaxDelay().toSeconds()));
    }
}
