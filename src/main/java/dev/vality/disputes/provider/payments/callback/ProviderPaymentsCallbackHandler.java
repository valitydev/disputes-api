package dev.vality.disputes.provider.payments.callback;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.client.ProviderPaymentsRemoteClient;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
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
    private final ProviderDataService providerDataService;
    private final ProviderCallbackDao providerCallbackDao;
    private final ProviderPaymentsRemoteClient providerPaymentsRemoteClient;

    @Value("${provider.payments.isProviderCallbackEnabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams callback) throws TException {
        log.info("Got providerPaymentsCallbackParams {}", callback);
        if (!enabled) {
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
            log.info("Got invoicePayment {}", invoicePayment);
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            var providerData = providerDataService.getProviderData(invoicePayment);
            var currency = providerDataService.getCurrency(invoicePayment);
            var providerTrxId = getProviderTrxId(invoicePayment);
            var paymentStatusResult = providerPaymentsRemoteClient.checkPaymentStatus(invoiceId, paymentId, providerTrxId, currency, providerData);
            if (paymentStatusResult.isSuccess()) {
                var providerCallback = new ProviderCallback();
                providerCallback.setInvoiceId(invoiceId);
                providerCallback.setPaymentId(paymentId);
                providerCallback.setChangedAmount(paymentStatusResult.getChangedAmount().orElse(null));
                providerCallback.setAmount(invoicePayment.getPayment().getCost().getAmount());
                providerCallbackDao.save(providerCallback);
                log.info("Saved providerCallback, finish {}", providerCallback);
            } else {
                log.info("remoteClient.checkPaymentStatus result was skipped by failed status, finish");
            }
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
