package dev.vality.disputes.provider.payments.converter;

import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.provider.payments.TransactionContext;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings({"LineLength"})
public class TransactionContextConverter {

    public TransactionContext convert(String invoiceId, String paymentId, String providerTrxId, ProviderData providerData) {
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(providerTrxId);
        transactionContext.setInvoiceId(invoiceId);
        transactionContext.setPaymentId(paymentId);
        transactionContext.setTerminalOptions(providerData.getOptions());
        return transactionContext;
    }
}
