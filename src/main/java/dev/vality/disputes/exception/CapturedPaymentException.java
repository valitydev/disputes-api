package dev.vality.disputes.exception;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePayment;
import lombok.Getter;

@Getter
public class CapturedPaymentException extends InvoicingPaymentStatusRestrictionsException {

    private final InvoicePayment invoicePayment;

    public CapturedPaymentException(InvoicePayment invoicePayment) {
        super(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        this.invoicePayment = invoicePayment;
    }
}
