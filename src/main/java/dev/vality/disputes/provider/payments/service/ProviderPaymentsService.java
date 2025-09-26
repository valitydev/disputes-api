package dev.vality.disputes.provider.payments.service;

import dev.vality.damsel.domain.Currency;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.CapturedPaymentException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.ProviderTrxIdNotFoundException;
import dev.vality.disputes.provider.payments.client.ProviderPaymentsRemoteClient;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
import dev.vality.disputes.provider.payments.converter.TransactionContextConverter;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackAlreadyExistException;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.provider.payments.exception.ProviderPaymentsUnexpectedPaymentStatus;
import dev.vality.disputes.schedule.converter.DisputeCurrencyConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.util.PaymentAmountUtil;
import dev.vality.disputes.util.PaymentStatusValidator;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static dev.vality.disputes.constant.ErrorMessage.INVOICE_NOT_FOUND;
import static dev.vality.disputes.constant.ErrorMessage.PAYMENT_NOT_FOUND;
import static dev.vality.disputes.util.ThreadFormatter.buildThreadName;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderPaymentsService {

    private final ProviderCallbackDao providerCallbackDao;
    private final InvoicingService invoicingService;
    private final TransactionContextConverter transactionContextConverter;
    private final ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter
            providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter;
    private final ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter
            providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter;
    private final DisputeCurrencyConverter disputeCurrencyConverter;
    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;
    private final ProviderDataService providerDataService;
    private final DisputesService disputesService;
    private final ProviderPaymentsRemoteClient providerPaymentsRemoteClient;

    @Async("disputesAsyncServiceExecutor")
    public void processCallback(ProviderPaymentsCallbackParams callback) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName(buildThreadName("providerPaymentsService.processCallback", oldName, callback));
        try {
            var invoiceId = callback.getInvoiceId().get();
            var paymentId = callback.getPaymentId().get();
            var invoicePayment = invoicingService.getInvoicePayment(invoiceId, paymentId);
            log.debug("Got invoicePayment {}", callback);
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            // validate
            var providerTrxId = getProviderTrxId(invoicePayment);
            var providerData = providerDataService.getProviderData(invoicePayment.getRoute().getProvider(),
                    invoicePayment.getRoute().getTerminal());
            var transactionContext =
                    transactionContextConverter.convert(invoiceId, paymentId, providerTrxId, providerData,
                            invoicePayment.getLastTransactionInfo());
            var currency = providerDataService.getCurrency(invoicePayment.getPayment().getCost().getCurrency());
            var invoiceAmount = invoicePayment.getPayment().getCost().getAmount();
            checkPaymentStatusAndCreateAdjustment(transactionContext, currency, providerData, invoiceAmount);
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.info("InvoicingPaymentStatusRestrictionsException when process ProviderPaymentsCallbackParams {}",
                    callback);
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle ProviderPaymentsCallbackParams, type={}", ex.getType(), ex);
        } catch (Throwable ex) {
            log.warn("Failed to handle ProviderPaymentsCallbackParams", ex);
        } finally {
            currentThread.setName(oldName);
        }
    }

    public void createAdjustment(Dispute dispute, ProviderData providerData, TransactionInfo transactionInfo) {
        var transactionContext = transactionContextConverter.convert(dispute.getInvoiceId(), dispute.getPaymentId(),
                dispute.getProviderTrxId(), providerData, transactionInfo);
        var currency = disputeCurrencyConverter.convert(dispute);
        try {
            checkPaymentStatusAndCreateAdjustment(transactionContext, currency, providerData, dispute.getAmount());
        } catch (ProviderCallbackAlreadyExistException ex) {
            log.warn("ProviderCallbackAlreadyExist when handle providerPaymentsService.checkPaymentStatusAndSave", ex);
        }
    }

    @Transactional
    public void checkPaymentStatusAndCreateAdjustment(TransactionContext transactionContext, Currency currency,
                                                      ProviderData providerData, long amount) {
        checkProviderCallbackExist(transactionContext.getInvoiceId(), transactionContext.getPaymentId());
        var paymentStatusResult =
                providerPaymentsRemoteClient.checkPaymentStatus(transactionContext, currency, providerData);
        if (paymentStatusResult.isSuccess()) {
            var providerCallback = new ProviderCallback();
            providerCallback.setInvoiceId(transactionContext.getInvoiceId());
            providerCallback.setPaymentId(transactionContext.getPaymentId());
            providerCallback.setChangedAmount(getChangedAmount(amount, paymentStatusResult));
            providerCallback.setAmount(amount);
            log.info("Save providerCallback {}", providerCallback);
            providerCallbackDao.save(providerCallback);
        } else {
            throw new ProviderPaymentsUnexpectedPaymentStatus(
                    "providerPaymentsService.checkPaymentStatusAndSave unsuccessful: Cant do createAdjustment");
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
            var invoicePayment = invoicingService.getInvoicePayment(providerCallback.getInvoiceId(),
                    providerCallback.getPaymentId());
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            if (createCashFlowAdjustment(providerCallback, invoicePayment)) {
                // pause for waiting finish createCashFlowAdjustment
                return;
            }
            createCapturedAdjustment(providerCallback, invoicePayment);
            finishSucceeded(providerCallback);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle ProviderPaymentsService.callHgForCreateAdjustment, type={}", ex.getType(),
                    ex);
            switch (ex.getType()) {
                case INVOICE -> finishFailed(providerCallback, INVOICE_NOT_FOUND);
                case PAYMENT -> finishFailed(providerCallback, PAYMENT_NOT_FOUND);
                case PROVIDERCALLBACK -> log.debug("ProviderCallback locked {}", providerCallback);
                default -> throw ex;
            }
        } catch (CapturedPaymentException ex) {
            log.warn("CapturedPaymentException when handle ProviderPaymentsService.callHgForCreateAdjustment", ex);
            var changedAmount = PaymentAmountUtil.getChangedAmount(ex.getInvoicePayment().getPayment());
            if (changedAmount != null) {
                providerCallback.setChangedAmount(changedAmount);
            }
            finishSucceeded(providerCallback);
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error(
                    "InvoicingPaymentRestrictionStatus when handle ProviderPaymentsService.callHgForCreateAdjustment",
                    ex);
            finishFailed(providerCallback, PaymentStatusValidator.getTechnicalErrorMessage(ex));
        } catch (ProviderCallbackStatusWasUpdatedByAnotherThreadException ex) {
            log.debug(
                    "ProviderCallbackStatusWasUpdatedByAnotherThread " +
                            "when handle ProviderPaymentsService.callHgForCreateAdjustment",
                    ex);
        }
    }

    public void finishSucceeded(ProviderCallback providerCallback) {
        log.info("Trying to set succeeded ProviderCallback status {}", providerCallback);
        providerCallback.setStatus(ProviderPaymentsStatus.succeeded);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to succeeded {}", providerCallback.getInvoiceId());
        disputeFinishSucceeded(providerCallback);
    }

    public void finishFailed(ProviderCallback providerCallback, String technicalErrorMessage) {
        log.warn("Trying to set failed ProviderCallback status with '{}' technicalErrorMessage, {}",
                technicalErrorMessage, providerCallback.getInvoiceId());
        providerCallback.setStatus(ProviderPaymentsStatus.failed);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to failed {}", providerCallback.getInvoiceId());
        disputeFinishFailed(providerCallback, technicalErrorMessage);
    }

    private String getProviderTrxId(InvoicePayment payment) {
        return Optional.ofNullable(payment.getLastTransactionInfo())
                .map(TransactionInfo::getId)
                .orElseThrow(() -> new ProviderTrxIdNotFoundException(
                        String.format("Payment with id: %s and filled ProviderTrxId not found!",
                                payment.getPayment().getId())));
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

    private boolean createCashFlowAdjustment(ProviderCallback providerCallback, InvoicePayment invoicePayment) {
        if (!providerPaymentsAdjustmentExtractor.isCashFlowAdjustmentByProviderPaymentsExist(invoicePayment,
                providerCallback)
                && (providerCallback.getAmount() != null
                && providerCallback.getChangedAmount() != null
                && !Objects.equals(providerCallback.getAmount(), providerCallback.getChangedAmount()))) {
            var cashFlowParams =
                    providerPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter.convert(providerCallback);
            invoicingService.createPaymentAdjustment(providerCallback.getInvoiceId(), providerCallback.getPaymentId(),
                    cashFlowParams);
            return true;
        } else {
            log.info("Creating CashFlowAdjustment was skipped {}", providerCallback);
            return false;
        }
    }

    private void createCapturedAdjustment(ProviderCallback providerCallback, InvoicePayment invoicePayment) {
        if (!providerPaymentsAdjustmentExtractor.isCapturedAdjustmentByProviderPaymentsExist(invoicePayment,
                providerCallback)) {
            var capturedParams =
                    providerPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter.convert(providerCallback);
            invoicingService.createPaymentAdjustment(providerCallback.getInvoiceId(), providerCallback.getPaymentId(),
                    capturedParams);
        } else {
            log.info("Creating CapturedAdjustment was skipped {}", providerCallback);
        }
    }

    private void disputeFinishSucceeded(ProviderCallback providerCallback) {
        try {
            disputesService.finishSucceeded(providerCallback.getInvoiceId(), providerCallback.getPaymentId(),
                    providerCallback.getChangedAmount());
        } catch (NotFoundException ex) {
            log.debug("NotFound when handle disputeFinishSucceeded, type={}", ex.getType(), ex);
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void disputeFinishFailed(ProviderCallback providerCallback, String technicalErrorMessage) {
        try {
            disputesService.finishFailed(
                    providerCallback.getInvoiceId(),
                    providerCallback.getPaymentId(),
                    technicalErrorMessage);
        } catch (NotFoundException ex) {
            log.debug("NotFound when handle disputeFinishFailed, type={}", ex.getType(), ex);
        } catch (Throwable ex) {
            log.error("Received exception while ProviderPaymentsService.disputeFinishSucceeded", ex);
        }
    }

    private void checkProviderCallbackExist(String invoiceId, String paymentId) {
        try {
            var providerCallback = providerCallbackDao.get(invoiceId, paymentId);
            log.debug("ProviderCallback exist {}", providerCallback);
            throw new ProviderCallbackAlreadyExistException();
        } catch (NotFoundException ignored) {
            log.debug("It's new provider callback");
        }
    }
}
