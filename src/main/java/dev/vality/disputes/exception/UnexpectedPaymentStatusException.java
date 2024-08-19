package dev.vality.disputes.exception;

import dev.vality.damsel.domain.InvoicePaymentStatus;
import lombok.Getter;

@Getter
public class UnexpectedPaymentStatusException extends RuntimeException {

    private final String status;

    public UnexpectedPaymentStatusException(InvoicePaymentStatus s) {
        super(String.format("Payment has unexpected status %s", s.getSetField().getFieldName()));
        status = s.getSetField().getFieldName();
    }
}
