package dev.vality.disputes.service;

import dev.vality.damsel.domain.Failure;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.DisputeStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.schedule.model.ProviderData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputesService {

    public static final Set<DisputeStatus> DISPUTE_PENDING_STATUSES = disputePendingStatuses();
    private final DisputeDao disputeDao;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    public void finishSucceeded(String invoiceId, String paymentId, Long changedAmount) {
        var dispute = Optional.of(disputeDao.getSkipLockedByInvoiceId(invoiceId, paymentId))
                .filter(d -> DISPUTE_PENDING_STATUSES.contains(d.getStatus()))
                .orElseThrow();
        finishSucceeded(dispute, changedAmount);
    }

    public void finishSucceeded(Dispute dispute, Long changedAmount) {
        log.info("Trying to set succeeded Dispute status {}", dispute);
        disputeDao.finishSucceeded(dispute.getId(), changedAmount);
        log.debug("Dispute status has been set to succeeded {}", dispute);
    }

    public void finishFailed(String invoiceId, String paymentId, String errorMessage) {
        var dispute = Optional.of(disputeDao.getSkipLockedByInvoiceId(invoiceId, paymentId))
                .filter(d -> DISPUTE_PENDING_STATUSES.contains(d.getStatus()))
                .orElseThrow();
        finishFailed(dispute, errorMessage);
    }

    public void finishFailed(Dispute dispute, String errorMessage) {
        log.warn("Trying to set failed Dispute status with '{}' errorMessage, {}", errorMessage, dispute.getId());
        disputeDao.finishFailed(dispute.getId(), errorMessage);
        log.debug("Dispute status has been set to failed {}", dispute.getId());
    }

    public void finishFailedWithMapping(Dispute dispute, String errorMessage, Failure failure) {
        log.warn("Trying to set failed Dispute status with '{}' errorMessage, '{}' mapping, {}", errorMessage, failure.getCode(), dispute.getId());
        disputeDao.finishFailedWithMapping(dispute.getId(), errorMessage, failure.getCode());
        log.debug("Dispute status has been set to failed, '{}' mapping, {}", failure.getCode(), dispute.getId());
    }

    public void finishCancelled(Dispute dispute, String mapping, String errorMessage) {
        log.warn("Trying to set cancelled Dispute status with '{}' errorMessage, '{}' mapping, {}", errorMessage, mapping, dispute.getId());
        disputeDao.finishCancelled(dispute.getId(), errorMessage, mapping);
        log.debug("Dispute status has been set to cancelled {}", dispute);
    }

    public void setNextStepToCreated(Dispute dispute, ProviderData providerData) {
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
        log.info("Trying to set created Dispute status {}", dispute.getId());
        disputeDao.setNextStepToCreated(dispute.getId(), nextCheckAfter);
        log.debug("Dispute status has been set to created {}", dispute.getId());
    }

    public void setNextStepToPending(Dispute dispute, ProviderData providerData) {
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
        log.info("Trying to set pending Dispute status {}", dispute);
        disputeDao.setNextStepToPending(dispute.getId(), nextCheckAfter);
        log.debug("Dispute status has been set to pending {}", dispute.getId());
    }

    public void setNextStepToCreateAdjustment(Dispute dispute, Long changedAmount) {
        log.info("Trying to set create_adjustment Dispute status {}", dispute);
        disputeDao.setNextStepToCreateAdjustment(dispute.getId(), changedAmount);
        log.debug("Dispute status has been set to create_adjustment {}", dispute.getId());
    }

    public void setNextStepToManualPending(Dispute dispute, String errorMessage) {
        log.warn("Trying to set manual_pending Dispute status with '{}' errorMessage, {}", errorMessage, dispute.getId());
        disputeDao.setNextStepToManualPending(dispute.getId(), errorMessage);
        log.debug("Dispute status has been set to manual_pending {}", dispute.getId());
    }

    public void setNextStepToAlreadyExist(Dispute dispute) {
        log.info("Trying to set already_exist_created Dispute status {}", dispute);
        disputeDao.setNextStepToAlreadyExist(dispute.getId());
        log.debug("Dispute status has been set to already_exist_created {}", dispute);
    }

    public void setNextStepToPoolingExpired(Dispute dispute, String errorMessage) {
        log.warn("Trying to set pooling_expired Dispute status with '{}' errorMessage, {}", errorMessage, dispute.getId());
        disputeDao.setNextStepToPoolingExpired(dispute.getId(), errorMessage);
        log.debug("Dispute status has been set to pooling_expired {}", dispute.getId());
    }

    public void updateNextPollingInterval(Dispute dispute, ProviderData providerData) {
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
        disputeDao.updateNextPollingInterval(dispute, nextCheckAfter);
    }

    public List<Dispute> getForgottenSkipLocked(int batchSize) {
        var locked = disputeDao.getForgottenSkipLocked(batchSize);
        if (!locked.isEmpty()) {
            log.debug("ForgottenSkipLocked has been found, size={}", locked.size());
        }
        return locked;
    }

    public List<Dispute> getCreatedSkipLocked(int batchSize) {
        var locked = disputeDao.getSkipLocked(batchSize, DisputeStatus.created);
        if (!locked.isEmpty()) {
            log.debug("CreatedSkipLocked has been found, size={}", locked.size());
        }
        return locked;
    }

    public List<Dispute> getPendingSkipLocked(int batchSize) {
        var locked = disputeDao.getSkipLocked(batchSize, DisputeStatus.pending);
        if (!locked.isEmpty()) {
            log.debug("PendingSkipLocked has been found, size={}", locked.size());
        }
        return locked;
    }

    public Dispute getSkipLocked(String disputeId) {
        return disputeDao.getSkipLocked(UUID.fromString(disputeId));
    }

    public Dispute getByInvoiceId(String invoiceId, String paymentId) {
        return disputeDao.getByInvoiceId(invoiceId, paymentId);
    }

    public Dispute getSkipLockedByInvoiceId(String invoiceId, String paymentId) {
        return disputeDao.getSkipLockedByInvoiceId(invoiceId, paymentId);
    }

    public void checkCreatedStatus(Dispute dispute) {
        var forUpdate = getSkipLocked(dispute.getId().toString());
        if (forUpdate.getStatus() != DisputeStatus.created) {
            throw new DisputeStatusWasUpdatedByAnotherThreadException();
        }
    }

    public void checkPendingStatus(Dispute dispute) {
        var forUpdate = getSkipLocked(dispute.getId().toString());
        if (forUpdate.getStatus() != DisputeStatus.pending) {
            throw new DisputeStatusWasUpdatedByAnotherThreadException();
        }
    }

    public void checkPendingStatuses(Dispute dispute) {
        var forUpdate = getSkipLocked(dispute.getId().toString());
        if (!DISPUTE_PENDING_STATUSES.contains(forUpdate.getStatus())) {
            throw new DisputeStatusWasUpdatedByAnotherThreadException();
        }
    }

    private static Set<DisputeStatus> disputePendingStatuses() {
        return Set.of(
                DisputeStatus.created,
                DisputeStatus.pending,
                DisputeStatus.manual_pending,
                DisputeStatus.create_adjustment,
                DisputeStatus.already_exist_created,
                DisputeStatus.pooling_expired);
    }
}
