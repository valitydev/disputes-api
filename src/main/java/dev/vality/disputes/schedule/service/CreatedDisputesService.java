package dev.vality.disputes.schedule.service;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.manualparsing.ManualParsingTopic;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_CAPTURED_BLOCKED;
import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_PROVIDERS_API_EXIST;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class CreatedDisputesService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final CreatedAttachmentsService createdAttachmentsService;
    private final InvoicingService invoicingService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final ProviderDataService providerDataService;
    private final ExternalGatewayChecker externalGatewayChecker;
    private final ManualParsingTopic manualParsingTopic;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getCreatedDisputesForUpdateSkipLocked");
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.created);
        log.debug("CreatedDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
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
            log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND);
            log.debug("Dispute status has been set to failed {}", dispute.getId());
            return;
        }
        var status = invoicePayment.getPayment().getStatus();
        if (!status.isSetCaptured() && !status.isSetCancelled() && !status.isSetFailed()) {
            // не создаем диспут, пока платеж не финален
            log.warn("Payment has non-final status {} {}", status, dispute.getId());
            return;
        }
        var attachments = createdAttachmentsService.getAttachments(dispute);
        if (attachments == null || attachments.isEmpty()) {
            log.error("Trying to set failed Dispute status with NO_ATTACHMENTS error reason {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.NO_ATTACHMENTS);
            log.debug("Dispute status has been set to failed {}", dispute.getId());
            return;
        }
        var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
        var options = providerData.getOptions();
        if ((status.isSetCaptured() && isCapturedBlockedForDispute(options))
                || isNotProvidersDisputesApiExist(options)) {
            // отправлять на ручной разбор, если выставлена опция
            // DISPUTE_FLOW_CAPTURED_BLOCKED или не выставлена DISPUTE_FLOW_PROVIDERS_API_EXIST
            log.warn("finishTaskWithManualParsingFlowActivation, options capt={}, apiExist={}", isCapturedBlockedForDispute(options), isNotProvidersDisputesApiExist(options));
            finishTaskWithManualParsingFlowActivation(dispute, attachments, DisputeStatus.manual_created);
            return;
        }
        try {
            var result = remoteClient.createDispute(dispute, attachments, providerData);
            finishTask(dispute, attachments, result, options);
        } catch (WRuntimeException e) {
            if (externalGatewayChecker.isNotProvidersDisputesApiExist(providerData, e)) {
                // отправлять на ручной разбор, если API диспутов на провайдере не реализовано
                // (тогда при тесте соединения вернется 404)
                log.warn("finishTaskWithManualParsingFlowActivation with externalGatewayChecker", e);
                finishTaskWithManualParsingFlowActivation(dispute, attachments, DisputeStatus.manual_created);
                return;
            }
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, List<Attachment> attachments, DisputeCreatedResult result, Map<String, String> options) {
        switch (result.getSetField()) {
            case SUCCESS_RESULT -> {
                var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, options);
                log.info("Trying to set pending Dispute status {}, {}", dispute, result);
                providerDisputeDao.save(new ProviderDispute(result.getSuccessResult().getProviderDisputeId(), dispute.getId()));
                disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
                log.debug("Dispute status has been set to pending {}", dispute.getId());
            }
            case FAIL_RESULT -> {
                var errorMessage = ErrorFormatter.getErrorMessage(result.getFailResult().getFailure());
                log.warn("Trying to set failed Dispute status {}, {}", dispute.getId(), errorMessage);
                disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
                log.debug("Dispute status has been set to failed {}", dispute.getId());
            }
            case ALREADY_EXIST_RESULT ->
                    finishTaskWithManualParsingFlowActivation(dispute, attachments, DisputeStatus.already_exist_created);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTaskWithManualParsingFlowActivation(Dispute dispute, List<Attachment> attachments, DisputeStatus disputeStatus) {
        manualParsingTopic.sendCreated(dispute, attachments, disputeStatus);
        log.info("Trying to set {} Dispute status {}", disputeStatus, dispute);
        disputeDao.update(dispute.getId(), disputeStatus);
        log.debug("Dispute status has been set to {} {}", disputeStatus, dispute.getId());
    }

    private boolean isCapturedBlockedForDispute(Map<String, String> options) {
        return options.containsKey(DISPUTE_FLOW_CAPTURED_BLOCKED);
    }

    private boolean isNotProvidersDisputesApiExist(Map<String, String> options) {
        return !options.containsKey(DISPUTE_FLOW_PROVIDERS_API_EXIST);
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
