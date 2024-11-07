package dev.vality.disputes.provider.payments.converter;

import dev.vality.damsel.domain.InvoicePaymentAdjustmentCashFlow;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderPaymentsToInvoicePaymentCashFlowAdjustmentParamsConverter {

    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(ProviderCallback providerCallback) {
        var captured = new InvoicePaymentCaptured();
        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        captured.setReason(reason);
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(providerCallback.getChangedAmount()));
        return params;
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(Long changedAmount) {
        return InvoicePaymentAdjustmentScenario.cash_flow(new InvoicePaymentAdjustmentCashFlow()
                .setNewAmount(changedAmount));
    }
}
