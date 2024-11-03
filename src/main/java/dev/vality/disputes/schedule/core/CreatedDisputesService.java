package dev.vality.disputes.schedule.core;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.catcher.WRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeCreateResultHandler;
import dev.vality.disputes.schedule.result.ErrorResultHandler;
import dev.vality.disputes.schedule.service.AttachmentsService;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.external.InvoicingService;
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
@SuppressWarnings({"MemberName", "ParameterName", "LineLength", "MissingSwitchDefault"})
public class CreatedDisputesService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final AttachmentsService attachmentsService;
    private final InvoicingService invoicingService;
    private final ProviderDataService providerDataService;
    private final DefaultRemoteClient defaultRemoteClient;
    private final DisputeCreateResultHandler disputeCreateResultHandler;
    private final ErrorResultHandler errorResultHandler;
    private final WRuntimeExceptionCatcher wRuntimeExceptionCatcher;

    @Transactional
    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int batchSize) {
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.created);
        if (!locked.isEmpty()) {
            log.debug("CreatedDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        }
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
            errorResultHandler.updateFailed(dispute, ErrorReason.PAYMENT_NOT_FOUND);
            return;
        }
        var attachments = attachmentsService.getAttachments(dispute);
        if (attachments == null || attachments.isEmpty()) {
            errorResultHandler.updateFailed(dispute, ErrorReason.NO_ATTACHMENTS);
            return;
        }
        var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
        var options = providerData.getOptions();
        if ((invoicePayment.getPayment().getStatus().isSetCaptured() && isCapturedBlockedForDispute(options))
                || isNotProviderDisputesApiExist(options)) {
            // отправлять на ручной разбор, если выставлена опция
            // DISPUTE_FLOW_CAPTURED_BLOCKED или не выставлена DISPUTE_FLOW_PROVIDERS_API_EXIST
            log.warn("Trying to call defaultRemoteClient.createDispute(), options capt={}, apiExist={}", isCapturedBlockedForDispute(options), isNotProviderDisputesApiExist(options));
            wRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                    () -> {
                        var result = defaultRemoteClient.createDispute(dispute, attachments, providerData);
                        finishTask(dispute, result, providerData);
                    },
                    e -> disputeCreateResultHandler.handleUnexpectedResultMapping(dispute, e));
            return;
        }
        wRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                () -> wRuntimeExceptionCatcher.catchProviderDisputesApiNotExist(
                        providerData,
                        () -> {
                            var result = remoteClient.createDispute(dispute, attachments, providerData);
                            finishTask(dispute, result, providerData);
                        },
                        () -> wRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                                () -> {
                                    var result = defaultRemoteClient.createDispute(dispute, attachments, providerData);
                                    finishTask(dispute, result, providerData);
                                },
                                e -> disputeCreateResultHandler.handleUnexpectedResultMapping(dispute, e))),
                e -> disputeCreateResultHandler.handleUnexpectedResultMapping(dispute, e));
    }

    @Transactional
    void finishTask(Dispute dispute, DisputeCreatedResult result, ProviderData providerData) {
        switch (result.getSetField()) {
            case SUCCESS_RESULT -> disputeCreateResultHandler.handleSuccessResult(dispute, result, providerData);
            case FAIL_RESULT -> disputeCreateResultHandler.handleFailResult(dispute, result);
            case ALREADY_EXIST_RESULT -> disputeCreateResultHandler.handleAlreadyExistResult(dispute);
        }
    }

    private boolean isCapturedBlockedForDispute(Map<String, String> options) {
        return options.containsKey(DISPUTE_FLOW_CAPTURED_BLOCKED);
    }

    private boolean isNotProviderDisputesApiExist(Map<String, String> options) {
        return !options.containsKey(DISPUTE_FLOW_PROVIDERS_API_EXIST);
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
