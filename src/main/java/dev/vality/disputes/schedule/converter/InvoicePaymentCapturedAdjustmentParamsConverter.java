package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.InvoicePaymentAdjustmentStatusChange;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.AdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicePaymentCapturedAdjustmentParamsConverter {

    private final AdjustmentExtractor adjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(Dispute dispute) {
        var captured = new InvoicePaymentCaptured();
        var reason = adjustmentExtractor.getReason(dispute);
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
