package dev.vality.disputes.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private final Type type;

    public NotFoundException(String message, Type type) {
        super(message);
        this.type = type;
    }

    public NotFoundException(String message, Throwable cause, Type type) {
        super(message, cause);
        this.type = type;
    }

    public enum Type {
        INVOICE,
        PAYMENT,
        ATTACHMENT,
        FILEMETA,
        TERMINAL,
        PROVIDER,
        PROXY,
        CURRENCY,
        PARTY,
        SHOP,
        PROVIDERTRXID,
        DISPUTE,
        PROVIDERDISPUTE,
        PROVIDERCALLBACK
    }
}
