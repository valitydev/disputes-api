package dev.vality.disputes.provider.payments.converter;

import dev.vality.damsel.domain.InvoicePaymentAdjustmentTransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderPaymentsToInvoicePaymentTransactionInfoAdjustmentParamsConverter {

    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;
    private final TransactionInfoThriftConverter transactionInfoThriftConverter;

    public InvoicePaymentAdjustmentParams convert(ProviderCallback providerCallback) {
        var transactionInfo = transactionInfoThriftConverter.deserialize(providerCallback.getTransactionInfo());
        return new InvoicePaymentAdjustmentParams()
                .setReason(providerPaymentsAdjustmentExtractor.getReason(providerCallback))
                .setScenario(InvoicePaymentAdjustmentScenario.transaction_info(
                        new InvoicePaymentAdjustmentTransactionInfo(transactionInfo)));
    }
}
