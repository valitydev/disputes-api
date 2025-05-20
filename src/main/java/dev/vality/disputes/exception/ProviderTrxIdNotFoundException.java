package dev.vality.disputes.exception;

public class ProviderTrxIdNotFoundException extends NotFoundException {

    public ProviderTrxIdNotFoundException(String message) {
        super(message, Type.PROVIDERTRXID);
    }
}
