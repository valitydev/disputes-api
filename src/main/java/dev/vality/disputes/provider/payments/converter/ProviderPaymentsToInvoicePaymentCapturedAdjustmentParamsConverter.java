package dev.vality.disputes.provider.payments.converter;

import dev.vality.damsel.domain.InvoicePaymentAdjustmentStatusChange;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderPaymentsToInvoicePaymentCapturedAdjustmentParamsConverter {

    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(ProviderCallback providerCallback) {
        var captured = new InvoicePaymentCaptured();
        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        captured.setReason(reason);
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(captured));
        return params;
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(InvoicePaymentCaptured captured) {
        return InvoicePaymentAdjustmentScenario.status_change(new InvoicePaymentAdjustmentStatusChange(
                InvoicePaymentStatus.captured(captured)));
    }
}
