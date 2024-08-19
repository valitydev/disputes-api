package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.Event;
import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;

import java.util.List;

public interface InvoicingService {

    Invoice getInvoice(String invoiceId);

    List<Event> getEvents(String invoiceId);

    InvoicePaymentAdjustment createPaymentAdjustment(
            String invoiceId,
            String paymentId,
            InvoicePaymentAdjustmentParams params);
}
