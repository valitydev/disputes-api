package dev.vality.disputes.service.external;

import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;

import java.util.Optional;

public interface InvoicingService {

    Invoice getInvoice(String invoiceId);

    InvoicePayment getInvoicePayment(String invoiceId, String paymentId);

    Optional<String> getInvoicePaymentRiskScore(String invoiceId, String paymentId);

    void createPaymentAdjustment(
            String invoiceId,
            String paymentId,
            InvoicePaymentAdjustmentParams params);

}
