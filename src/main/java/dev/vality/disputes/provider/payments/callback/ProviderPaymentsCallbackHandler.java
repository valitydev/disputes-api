package dev.vality.disputes.provider.payments.callback;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.converter.TransactionContextConverter;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsCallbackHandler implements ProviderPaymentsCallbackServiceSrv.Iface {

    private final InvoicingService invoicingService;
    private final ProviderPaymentsService providerPaymentsService;
    private final ProviderDataService providerDataService;
    private final TransactionContextConverter transactionContextConverter;

    @Value("${provider.payments.isProviderCallbackEnabled}")
    private boolean isProviderCallbackEnabled;

    @Override
    @Transactional
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams callback) throws TException {
        log.info("Got providerPaymentsCallbackParams {}", callback);
        if (!isProviderCallbackEnabled) {
            return;
        }
        if (callback.getInvoiceId().isEmpty() && callback.getPaymentId().isEmpty()) {
            log.info("InvoiceId should be set, finish");
            return;
        }
        try {
            var invoiceId = callback.getInvoiceId().get();
            var paymentId = callback.getPaymentId().get();
            var invoicePayment = invoicingService.getInvoicePayment(invoiceId, paymentId);
            log.debug("Got invoicePayment {}", callback);
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            // validate
            var providerTrxId = getProviderTrxId(invoicePayment);
            var providerData = providerDataService.getProviderData(invoicePayment);
            var transactionContext = transactionContextConverter.convert(invoiceId, paymentId, providerTrxId, providerData);
            var currency = providerDataService.getCurrency(invoicePayment);
            var invoiceAmount = invoicePayment.getPayment().getCost().getAmount();
            providerPaymentsService.checkPaymentStatusAndSave(transactionContext, currency, providerData, invoiceAmount);
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle ProviderPaymentsCallbackParams, type={}", ex.getType(), ex);
        } catch (Throwable ex) {
            log.warn("Failed to handle ProviderPaymentsCallbackParams", ex);
        }
    }

    private String getProviderTrxId(InvoicePayment payment) {
        return Optional.ofNullable(payment.getLastTransactionInfo())
                .map(TransactionInfo::getId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Payment with id: %s and filled ProviderTrxId not found!", payment.getPayment().getId()), NotFoundException.Type.PROVIDERTRXID));
    }
}
