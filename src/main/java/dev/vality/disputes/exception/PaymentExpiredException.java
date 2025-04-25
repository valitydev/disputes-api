package dev.vality.disputes.exception;

public class PaymentExpiredException extends RuntimeException {

    public PaymentExpiredException(String message) {
        super(message);
    }
}
