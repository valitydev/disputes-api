package dev.vality.disputes.provider.payments.service;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackAlreadyExistException;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void checkProviderCallbackExist(String invoiceId, String paymentId) {
        try {
            providerCallbackDao.get(invoiceId, paymentId);
            throw new ProviderCallbackAlreadyExistException();
        } catch (NotFoundException ignored) {
            log.debug("It's new provider callback");
        }
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
            finishSucceeded(providerCallback);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle ProviderPaymentsService.callHgForCreateAdjustment, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> finishFailed(providerCallback, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> finishFailed(providerCallback, ErrorMessage.PAYMENT_NOT_FOUND);
                case PROVIDERCALLBACK -> log.debug("ProviderCallback locked {}", providerCallbackDao);
                default -> throw ex;
            }
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle ProviderPaymentsService.callHgForCreateAdjustment", ex);
            finishFailed(providerCallback, PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex));
        } catch (ProviderCallbackStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("ProviderCallbackStatusWasUpdatedByAnotherThread when handle ProviderPaymentsService.callHgForCreateAdjustment", ex);
        }
    }

    private void checkCreateAdjustmentStatus(ProviderCallback providerCallback) {
        var forUpdate = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(providerCallback.getId());
        if (forUpdate.getStatus() != ProviderPaymentsStatus.create_adjustment) {
            throw new ProviderCallbackStatusWasUpdatedByAnotherThreadException();
        }
    }

    private void createCashFlowAdjustment(ProviderCallback providerCallback, InvoicePayment invoicePayment) {
        if (!providerPaymentsAdjustmentExtractor.isCashFlowAdjustmentByProviderPaymentsExist(invoicePayment, providerCallback)
                && !Objects.equals(providerCallback.getAmount(), providerCallback.getChangedAmount())) {
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

    private void finishSucceeded(ProviderCallback providerCallback) {
        log.info("Trying to set succeeded ProviderCallback status {}", providerCallback);
        providerCallback.setStatus(ProviderPaymentsStatus.succeeded);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to succeeded {}", providerCallback.getInvoiceId());
        disputeFinishSucceeded(providerCallback);
    }

    private void finishFailed(ProviderCallback providerCallback, String errorReason) {
        log.warn("Trying to set failed ProviderCallback status with '{}' errorReason, {}", errorReason, providerCallback.getInvoiceId());
        providerCallback.setStatus(ProviderPaymentsStatus.failed);
        providerCallback.setErrorReason(errorReason);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to failed {}", providerCallback.getInvoiceId());
        disputeFinishSucceeded(providerCallback, errorReason);
    }

    private void disputeFinishSucceeded(ProviderCallback providerCallback) {
        try {
            disputesService.finishSucceeded(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), providerCallback.getChangedAmount());
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void disputeFinishSucceeded(ProviderCallback providerCallback, String errorMessage) {
        try {
            disputesService.finishFailed(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), errorMessage);
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }
}
