package dev.vality.disputes.provider.payments.service;

import dev.vality.damsel.domain.Currency;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.client.ProviderPaymentsRemoteClient;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackAlreadyExistException;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsService {

    private final ProviderCallbackDao providerCallbackDao;
    private final InvoicingService invoicingService;
    private final ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
    private final ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;
    private final DisputesService disputesService;
    private final ProviderPaymentsRemoteClient providerPaymentsRemoteClient;

    @Value("${provider.payments.isSkipCallHgForCreateAdjustment}")
    private boolean isSkipCallHgForCreateAdjustment;

    public PaymentStatusResult checkPaymentStatusAndSave(TransactionContext transactionContext, Currency currency, ProviderData providerData, long amount) {
        checkProviderCallbackExist(transactionContext.getInvoiceId(), transactionContext.getPaymentId());
        var paymentStatusResult = providerPaymentsRemoteClient.checkPaymentStatus(transactionContext, currency, providerData);
        if (paymentStatusResult.isSuccess()) {
            var providerCallback = new ProviderCallback();
            providerCallback.setInvoiceId(transactionContext.getInvoiceId());
            providerCallback.setPaymentId(transactionContext.getPaymentId());
            providerCallback.setChangedAmount(getChangedAmount(amount, paymentStatusResult));
            providerCallback.setAmount(amount);
            providerCallback.setSkipCallHgForCreateAdjustment(isSkipCallHgForCreateAdjustment);
            log.info("Save providerCallback {}", providerCallback);
            providerCallbackDao.save(providerCallback);
        } else {
            log.info("providerPaymentsRemoteClient.checkPaymentStatus result was skipped by failed status");
        }
        return paymentStatusResult;
    }

    @Transactional
    public List<ProviderCallback> getPaymentsForHgCall(int batchSize) {
        var locked = providerCallbackDao.getProviderCallbacksForHgCall(batchSize);
        if (!locked.isEmpty()) {
            log.debug("getProviderCallbackForHgCall has been found, size={}", locked.size());
        }
        return locked;
    }

    @Transactional
    public void callHgForCreateAdjustment(ProviderCallback providerCallback) {
        try {
            // validate
            checkCreateAdjustmentStatus(providerCallback);
            // validate
            var invoicePayment = invoicingService.getInvoicePayment(providerCallback.getInvoiceId(), providerCallback.getPaymentId());
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            createCashFlowAdjustment(providerCallback, invoicePayment);
            createCapturedAdjustment(providerCallback, invoicePayment);
            finishSucceeded(providerCallback, true);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle ProviderPaymentsService.callHgForCreateAdjustment, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> finishFailed(providerCallback, ErrorMessage.INVOICE_NOT_FOUND, true);
                case PAYMENT -> finishFailed(providerCallback, ErrorMessage.PAYMENT_NOT_FOUND, true);
                case PROVIDERCALLBACK -> log.debug("ProviderCallback locked {}", providerCallbackDao);
                default -> throw ex;
            }
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle ProviderPaymentsService.callHgForCreateAdjustment", ex);
            finishFailed(providerCallback, PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex), true);
        } catch (ProviderCallbackStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("ProviderCallbackStatusWasUpdatedByAnotherThread when handle ProviderPaymentsService.callHgForCreateAdjustment", ex);
        }
    }

    public void finishSucceeded(ProviderCallback providerCallback, boolean isDisputeSucceeded) {
        log.info("Trying to set succeeded ProviderCallback status {}", providerCallback);
        providerCallback.setStatus(ProviderPaymentsStatus.succeeded);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to succeeded {}", providerCallback.getInvoiceId());
        if (isDisputeSucceeded) {
            disputeFinishSucceeded(providerCallback);
        }
    }

    public void finishFailed(ProviderCallback providerCallback, String errorReason, boolean isDisputeFailed) {
        log.warn("Trying to set failed ProviderCallback status with '{}' errorReason, {}", errorReason, providerCallback.getInvoiceId());
        if (errorReason != null) {
            providerCallback.setErrorReason(errorReason);
        }
        providerCallback.setStatus(ProviderPaymentsStatus.failed);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to failed {}", providerCallback.getInvoiceId());
        if (isDisputeFailed) {
            disputeFinishFailed(providerCallback, errorReason);
        }
    }

    public void finishCancelled(ProviderCallback providerCallback, String errorReason, boolean isDisputeCancelled) {
        log.warn("Trying to set cancelled ProviderCallback status with '{}' errorReason, {}", errorReason, providerCallback.getInvoiceId());
        if (errorReason != null) {
            providerCallback.setErrorReason(errorReason);
        }
        providerCallback.setStatus(ProviderPaymentsStatus.cancelled);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to cancelled {}", providerCallback.getInvoiceId());
        if (isDisputeCancelled) {
            disputeFinishCancelled(providerCallback, errorReason);
        }
    }

    private Long getChangedAmount(long amount, PaymentStatusResult paymentStatusResult) {
        return paymentStatusResult.getChangedAmount()
                .filter(changedAmount -> changedAmount != amount)
                .orElse(null);
    }

    private void checkCreateAdjustmentStatus(ProviderCallback providerCallback) {
        var forUpdate = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(providerCallback.getId());
        if (forUpdate.getStatus() != ProviderPaymentsStatus.create_adjustment) {
            throw new ProviderCallbackStatusWasUpdatedByAnotherThreadException();
        }
    }

    private void createCashFlowAdjustment(ProviderCallback providerCallback, InvoicePayment invoicePayment) {
        if (!providerPaymentsAdjustmentExtractor.isCashFlowAdjustmentByProviderPaymentsExist(invoicePayment, providerCallback)
                && (providerCallback.getAmount() != null
                && providerCallback.getChangedAmount() != null
                && !Objects.equals(providerCallback.getAmount(), providerCallback.getChangedAmount()))) {
            var cashFlowParams = providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter.convert(providerCallback);
            invoicingService.createPaymentAdjustment(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), cashFlowParams);
        } else {
            log.info("Creating CashFlowAdjustment was skipped {}", providerCallback);
        }
    }

    private void createCapturedAdjustment(ProviderCallback providerCallback, InvoicePayment invoicePayment) {
        if (!providerPaymentsAdjustmentExtractor.isCapturedAdjustmentByProviderPaymentsExist(invoicePayment, providerCallback)) {
            var capturedParams = providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter.convert(providerCallback);
            invoicingService.createPaymentAdjustment(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), capturedParams);
        } else {
            log.info("Creating CapturedAdjustment was skipped {}", providerCallback);
        }
    }

    private void disputeFinishSucceeded(ProviderCallback providerCallback) {
        try {
            disputesService.finishSucceeded(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), providerCallback.getChangedAmount());
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void disputeFinishFailed(ProviderCallback providerCallback, String errorMessage) {
        try {
            disputesService.finishFailed(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), errorMessage);
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void disputeFinishCancelled(ProviderCallback providerCallback, String errorMessage) {
        try {
            disputesService.finishCancelled(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), errorMessage);
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void checkProviderCallbackExist(String invoiceId, String paymentId) {
        try {
            providerCallbackDao.get(invoiceId, paymentId);
            throw new ProviderCallbackAlreadyExistException();
        } catch (NotFoundException ignored) {
            log.debug("It's new provider callback");
        }
    }
}
