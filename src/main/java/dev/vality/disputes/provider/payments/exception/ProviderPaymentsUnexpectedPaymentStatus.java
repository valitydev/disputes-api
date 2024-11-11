package dev.vality.disputes.provider.payments.exception;

public class ProviderPaymentsUnexpectedPaymentStatus extends RuntimeException {

    public ProviderPaymentsUnexpectedPaymentStatus(String message) {
        super(message);
    }
}
