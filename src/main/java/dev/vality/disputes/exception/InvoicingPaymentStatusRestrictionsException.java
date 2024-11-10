package dev.vality.disputes.exception;

import dev.vality.damsel.domain.InvoicePaymentStatus;
import lombok.Getter;

@Getter
public class InvoicingPaymentStatusRestrictionsException extends RuntimeException {

    private final InvoicePaymentStatus status;

    public InvoicingPaymentStatusRestrictionsException(InvoicePaymentStatus status) {
        this.status = status;
    }

    public InvoicingPaymentStatusRestrictionsException(Throwable cause, InvoicePaymentStatus status) {
        super(cause);
        this.status = status;
    }
}
