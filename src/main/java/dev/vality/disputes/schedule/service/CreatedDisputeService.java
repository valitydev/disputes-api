package dev.vality.disputes.schedule.service;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.DisputeCreatedResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class CreatedDisputeService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final CreatedAttachmentsService createdAttachmentsService;
    private final InvoicingService invoicingService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getCreatedDisputesForUpdateSkipLocked");
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.created);
        log.debug("CreatedDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
    public void callCreateDisputeRemotely(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.created) {
            log.debug("Dispute locked or wrong status {}", forUpdate);
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        var invoicePayment = getInvoicePayment(dispute);
        if (invoicePayment == null || !invoicePayment.isSetRoute()) {
            log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND);
            log.debug("Dispute status has been set to failed {}", dispute);
            return;
        }
        var status = invoicePayment.getPayment().getStatus();
        if (!status.isSetCaptured() && !status.isSetCancelled() && !status.isSetFailed()) {
            // не создаем диспут, пока платеж не финален
            log.warn("Payment has non-final status {} {}", status, dispute);
            return;
        }
        var attachments = createdAttachmentsService.getAttachments(dispute);
        if (attachments == null || attachments.isEmpty()) {
            log.error("Trying to set failed Dispute status with NO_ATTACHMENTS error reason {}", dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.NO_ATTACHMENTS);
            log.debug("Dispute status has been set to failed {}", dispute);
            return;
        }
        var result = remoteClient.createDispute(dispute, attachments);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeCreatedResult result) {
        switch (result.getSetField()) {
            case SUCCESS_RESULT -> {
                var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
                log.info("Trying to set pending Dispute status {}, {}", dispute, result);
                providerDisputeDao.save(new ProviderDispute(result.getSuccessResult().getDisputeId(), dispute.getId()));
                disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
                log.debug("Dispute status has been set to pending {}", dispute);
            }
            case FAIL_RESULT -> {
                var errorMessage = TErrorUtil.toStringVal(result.getFailResult().getFailure());
                log.warn("Trying to set failed Dispute status {}, {}", dispute, errorMessage);
                disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
                log.debug("Dispute status has been set to failed {}", dispute);
            }
        }
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
