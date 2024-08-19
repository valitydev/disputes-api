package dev.vality.disputes.exception;

import dev.vality.swag.disputes.model.GeneralError;
import lombok.Getter;

@Getter
public class ProviderErrorException extends RuntimeException {

    private final GeneralError generalError;

    public ProviderErrorException(GeneralError generalError) {
        super(String.format("Received error from provider: %s", generalError.getMessage()));
        this.generalError = generalError;
    }
}
