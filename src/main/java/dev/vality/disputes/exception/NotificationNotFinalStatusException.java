package dev.vality.disputes.exception;

public class NotificationNotFinalStatusException extends RuntimeException {

    public NotificationNotFinalStatusException(String format) {
        super(format);
    }
}
