package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.AdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicePaymentFailedAdjustmentParamsConverter {

    private final AdjustmentExtractor adjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(Dispute dispute) {
        var invoicePaymentFailed = new InvoicePaymentFailed();
        var reason = adjustmentExtractor.getReason(dispute);
        invoicePaymentFailed.setFailure(OperationFailure.failure(
                new Failure("fake_failed_by_disputes_api").setReason(reason)));
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
