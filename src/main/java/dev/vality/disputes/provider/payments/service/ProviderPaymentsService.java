package dev.vality.disputes.provider.payments.service;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentFailedAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.handler.ProviderPaymentsErrorResultHandler;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderPaymentsService {

    private final ProviderCallbackDao providerCallbackDao;
    private final InvoicingService invoicingService;
    private final ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
    private final ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
    private final ProviderPaymentsToInvoicePaymentFailedAdjustmentParamsConverter providerPaymentsToInvoicePaymentFailedAdjustmentParamsConverter;
    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;
    private final ProviderPaymentsErrorResultHandler paymentsErrorResultHandler;
    private final DisputesService disputesService;

    @Transactional
    public List<ProviderCallback> getPaymentsForHgCall(int batchSize) {
        var locked = providerCallbackDao.getProviderCallbacksForHgCall(batchSize);
        if (!locked.isEmpty()) {
            log.debug("getProviderCallbackForHgCall has been found, size={}", locked.size());
        }
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void callHgForCreateAdjustment(ProviderCallback providerCallback) {
        log.debug("Trying to getProviderCallbackForUpdateSkipLocked {}", providerCallback);
        var forUpdate = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(providerCallback.getId());
        if (forUpdate == null || forUpdate.getStatus() != ProviderPaymentsStatus.create_adjustment) {
            log.debug("ProviderCallback locked or wrong status {}", forUpdate);
            return;
        }
        log.debug("GetProviderCallbackForUpdateSkipLocked has been found {}", providerCallback);
        var invoicePayment = getInvoicePayment(providerCallback);
        if (invoicePayment == null || !invoicePayment.isSetRoute()) {
            paymentsErrorResultHandler.updateFailed(providerCallback, ErrorReason.PAYMENT_NOT_FOUND);
            return;
        }
        if (!providerPaymentsAdjustmentExtractor.isCashFlowAdjustmentByProviderPaymentsExist(invoicePayment, providerCallback)
                && !Objects.equals(providerCallback.getAmount(), providerCallback.getChangedAmount())) {
            var params = providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter.convert(providerCallback);
            if (!createAdjustment(providerCallback, params)) {
                return;
            }
        } else {
            log.info("Creating CashFlowAdjustment was skipped {}", providerCallback);
        }
        if (!providerPaymentsAdjustmentExtractor.isCapturedAdjustmentByProviderPaymentsExist(invoicePayment, providerCallback)) {
            if (invoicePayment.getPayment().getStatus().isSetCaptured()) {
                var params = providerPaymentsToInvoicePaymentFailedAdjustmentParamsConverter.convert(providerCallback);
                if (!createAdjustment(providerCallback, params)) {
                    return;
                }
            }
            var params = providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter.convert(providerCallback);
            if (!createAdjustment(providerCallback, params)) {
                return;
            }
        } else {
            log.info("Creating CapturedAdjustment was skipped {}", providerCallback);
        }
        log.info("Trying to set succeeded ProviderCallback status {}", providerCallback);
        providerCallback.setStatus(ProviderPaymentsStatus.succeeded);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to succeeded {}", providerCallback.getInvoiceId());
        try {
            disputesService.finishSuccess(providerCallback.getInvoiceId(), providerCallback.getPaymentId());
        } catch (Throwable ex) {
            log.error("Received exception while disputesService.finishSuccess", ex);
        }
    }

    @Transactional
    boolean createAdjustment(ProviderCallback providerCallback, InvoicePaymentAdjustmentParams params) {
        var paymentAdjustment = createPaymentAdjustment(providerCallback, params);
        if (paymentAdjustment == null) {
            paymentsErrorResultHandler.updateFailed(providerCallback, ErrorReason.INVOICE_NOT_FOUND);
            return false;
        }
        return true;
    }

    private InvoicePaymentAdjustment createPaymentAdjustment(ProviderCallback providerCallback, InvoicePaymentAdjustmentParams params) {
        return invoicingService.createPaymentAdjustment(providerCallback.getInvoiceId(), providerCallback.getPaymentId(), params);
    }

    private InvoicePayment getInvoicePayment(ProviderCallback providerCallback) {
        return invoicingService.getInvoicePayment(providerCallback.getInvoiceId(), providerCallback.getPaymentId());
    }
}
