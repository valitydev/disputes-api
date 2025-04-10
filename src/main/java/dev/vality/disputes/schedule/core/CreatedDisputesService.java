package dev.vality.disputes.schedule.core;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.CapturedPaymentException;
import dev.vality.disputes.exception.DisputeStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.provider.DisputeStatusSuccessResult;
import dev.vality.disputes.provider.payments.client.ProviderPaymentsRemoteClient;
import dev.vality.disputes.provider.payments.converter.TransactionContextConverter;
import dev.vality.disputes.schedule.catcher.WoodyRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.converter.DisputeCurrencyConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeCreateResultHandler;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.AttachmentsService;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.util.PaymentStatusValidator;
import dev.vality.provider.payments.PaymentStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_PROVIDERS_API_EXIST;
import static dev.vality.disputes.util.PaymentAmountUtil.getChangedAmount;

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
    private final TransactionContextConverter transactionContextConverter;
    private final DisputeCurrencyConverter disputeCurrencyConverter;
    private final DefaultRemoteClient defaultRemoteClient;
    private final ProviderPaymentsRemoteClient providerPaymentsRemoteClient;
    private final DisputeCreateResultHandler disputeCreateResultHandler;
    private final DisputeStatusResultHandler disputeStatusResultHandler;
    private final WoodyRuntimeExceptionCatcher woodyRuntimeExceptionCatcher;

    @Transactional
    public List<Dispute> getCreatedSkipLocked(int batchSize) {
        return disputesService.getCreatedSkipLocked(batchSize);
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
            var providerStatus = checkProviderPaymentStatus(dispute, providerData, invoicePayment.getLastTransactionInfo());
            if (providerStatus.isSuccess()) {
                handleSucceededResultWithCreateAdjustment(dispute, providerStatus, providerData, invoicePayment.getLastTransactionInfo());
                return;
            }
            var finishCreateDisputeResult = (Consumer<DisputeCreatedResult>) result -> {
                switch (result.getSetField()) {
                    case SUCCESS_RESULT ->
                            disputeCreateResultHandler.handleCheckStatusResult(dispute, result, providerData);
                    case FAIL_RESULT -> disputeCreateResultHandler.handleFailedResult(dispute, result);
                    case ALREADY_EXIST_RESULT -> disputeCreateResultHandler.handleAlreadyExistResult(dispute);
                    case RETRY_LATER -> disputeCreateResultHandler.handleRetryLaterResult(dispute, providerData);
                    default -> throw new IllegalArgumentException(result.getSetField().getFieldName());
                }
            };
            var attachments = attachmentsService.getAttachments(dispute);
            var createDisputeByRemoteClient = (Runnable) () -> finishCreateDisputeResult.accept(
                    remoteClient.createDispute(dispute, attachments, providerData, invoicePayment.getLastTransactionInfo()));
            var createDisputeByDefaultClient = (Runnable) () -> finishCreateDisputeResult.accept(
                    defaultRemoteClient.createDispute(dispute, attachments, providerData, invoicePayment.getLastTransactionInfo()));
            if (providerData.getOptions().containsKey(DISPUTE_FLOW_PROVIDERS_API_EXIST)) {
                createDisputeByRemoteClient(dispute, providerData, createDisputeByRemoteClient, createDisputeByDefaultClient);
            } else {
                log.info("Trying to call defaultRemoteClient.createDispute() by case options!=DISPUTE_FLOW_PROVIDERS_API_EXIST");
                createDisputeByDefaultClient(dispute, createDisputeByDefaultClient);
            }
        } catch (NotFoundException ex) {
            log.error("NotFound when handle CreatedDisputesService.callCreateDisputeRemotely, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> disputeCreateResultHandler.handleFailedResult(dispute, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> disputeCreateResultHandler.handleFailedResult(dispute, ErrorMessage.PAYMENT_NOT_FOUND);
                case ATTACHMENT, FILEMETA ->
                        disputeCreateResultHandler.handleFailedResult(dispute, ErrorMessage.NO_ATTACHMENTS);
                case DISPUTE -> log.debug("Dispute locked {}", dispute);
                default -> throw ex;
            }
        } catch (CapturedPaymentException ex) {
            log.info("CapturedPaymentException when handle CreatedDisputesService.callCreateDisputeRemotely", ex);
            disputeCreateResultHandler.handleSucceededResult(dispute, getChangedAmount(ex.getInvoicePayment().getPayment()));
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle CreatedDisputesService.callCreateDisputeRemotely", ex);
            disputeCreateResultHandler.handleFailedResult(dispute, PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex));
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

    private PaymentStatusResult checkProviderPaymentStatus(Dispute dispute, ProviderData providerData, TransactionInfo transactionInfo) {
        var transactionContext = transactionContextConverter.convert(dispute.getInvoiceId(), dispute.getPaymentId(), dispute.getProviderTrxId(), providerData, transactionInfo);
        var currency = disputeCurrencyConverter.convert(dispute);
        return providerPaymentsRemoteClient.checkPaymentStatus(transactionContext, currency, providerData);
    }

    private void handleSucceededResultWithCreateAdjustment(Dispute dispute, PaymentStatusResult providerStatus, ProviderData providerData, TransactionInfo transactionInfo) {
        disputeStatusResultHandler.handleSucceededResult(
                dispute, getDisputeStatusResult(providerStatus.getChangedAmount().orElse(null)), providerData, transactionInfo);
    }

    private DisputeStatusResult getDisputeStatusResult(Long changedAmount) {
        return Optional.ofNullable(changedAmount)
                .map(amount -> DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult().setChangedAmount(amount)))
                .orElse(DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult()));
    }
}
