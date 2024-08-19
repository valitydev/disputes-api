package dev.vality.disputes.exception;

public class BouncerException extends RuntimeException {

    public BouncerException(String s) {
        super(s);
    }

    public BouncerException(String message, Throwable cause) {
        super(message, cause);
    }
}
