package dev.vality.disputes.schedule.core;

import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.DisputeStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.catcher.WoodyRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeCreateResultHandler;
import dev.vality.disputes.schedule.service.AttachmentsService;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_PROVIDERS_API_EXIST;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class CreatedDisputesService {

    private final RemoteClient remoteClient;
    private final DisputesService disputesService;
    private final AttachmentsService attachmentsService;
    private final InvoicingService invoicingService;
    private final ProviderDataService providerDataService;
    private final DefaultRemoteClient defaultRemoteClient;
    private final DisputeCreateResultHandler disputeCreateResultHandler;
    private final WoodyRuntimeExceptionCatcher woodyRuntimeExceptionCatcher;

    @Transactional
    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int batchSize) {
        return disputesService.getCreatedDisputesForUpdateSkipLocked(batchSize);
    }

    @Transactional
    public void callCreateDisputeRemotely(Dispute dispute) {
        try {
            // validate
            disputesService.checkCreatedStatus(dispute);
            // validate
            var invoicePayment = invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var finishCreateDisputeResult = (Consumer<DisputeCreatedResult>) result -> {
                switch (result.getSetField()) {
                    case SUCCESS_RESULT -> disputeCreateResultHandler.handleSuccessResult(
                            dispute, result, providerData);
                    case FAIL_RESULT -> disputeCreateResultHandler.handleFailResult(dispute, result);
                    case ALREADY_EXIST_RESULT -> disputeCreateResultHandler.handleAlreadyExistResult(dispute);
                    default -> throw new IllegalArgumentException(result.getSetField().getFieldName());
                }
            };
            var attachments = attachmentsService.getAttachments(dispute);
            var createDisputeByRemoteClient = (Runnable) () -> finishCreateDisputeResult.accept(
                    remoteClient.createDispute(dispute, attachments, providerData));
            var createDisputeByDefaultClient = (Runnable) () -> finishCreateDisputeResult.accept(
                    defaultRemoteClient.createDispute(dispute, attachments, providerData));
            var options = providerData.getOptions();
            if (options.containsKey(DISPUTE_FLOW_PROVIDERS_API_EXIST)) {
                createDisputeByRemoteClient(dispute, providerData, createDisputeByRemoteClient, createDisputeByDefaultClient);
            } else {
                log.info("Trying to call defaultRemoteClient.createDispute() by case options!=DISPUTE_FLOW_PROVIDERS_API_EXIST");
                createDisputeByDefaultClient(dispute, createDisputeByDefaultClient);
            }
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle CreatedDisputesService.callCreateDisputeRemotely, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> disputeCreateResultHandler.handleFailResult(dispute, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> disputeCreateResultHandler.handleFailResult(dispute, ErrorMessage.PAYMENT_NOT_FOUND);
                case ATTACHMENT, FILEMETA ->
                        disputeCreateResultHandler.handleFailResult(dispute, ErrorMessage.NO_ATTACHMENTS);
                case DISPUTE -> log.debug("Dispute locked {}", dispute);
                default -> throw ex;
            }
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatusException when handle CreatedDisputesService.callCreateDisputeRemotely", ex);
            disputeCreateResultHandler.handleFailResult(dispute, ErrorMessage.PAYMENT_STATUS_RESTRICTIONS);
        } catch (DisputeStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("DisputeStatusWasUpdatedByAnotherThread when handle CreatedDisputesService.callCreateDisputeRemotely", ex);
        }
    }

    private void createDisputeByRemoteClient(Dispute dispute, ProviderData providerData, Runnable createDisputeByRemoteClient, Runnable createDisputeByDefaultClient) {
        woodyRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                () -> woodyRuntimeExceptionCatcher.catchProviderDisputesApiNotExist(
                        providerData,
                        createDisputeByRemoteClient,
                        () -> createDisputeByDefaultClient(dispute, createDisputeByDefaultClient)),
                ex -> disputeCreateResultHandler.handleUnexpectedResultMapping(dispute, ex));
    }

    private void createDisputeByDefaultClient(Dispute dispute, Runnable createDisputeByDefaultClient) {
        woodyRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                createDisputeByDefaultClient,
                ex -> disputeCreateResultHandler.handleUnexpectedResultMapping(dispute, ex));
    }
}
