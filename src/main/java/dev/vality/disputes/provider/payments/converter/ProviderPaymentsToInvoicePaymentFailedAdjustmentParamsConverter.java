package dev.vality.disputes.provider.payments.converter;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderPaymentsToInvoicePaymentFailedAdjustmentParamsConverter {

    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(ProviderCallback providerCallback) {
        var invoicePaymentFailed = new InvoicePaymentFailed();
        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        invoicePaymentFailed.setFailure(OperationFailure.failure(
                new Failure("failed_by_provider_payments_flow").setReason(reason)));
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(invoicePaymentFailed));
        return params;
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(InvoicePaymentFailed failed) {
        return InvoicePaymentAdjustmentScenario.status_change(new InvoicePaymentAdjustmentStatusChange(
                InvoicePaymentStatus.failed(failed)));
    }
}
