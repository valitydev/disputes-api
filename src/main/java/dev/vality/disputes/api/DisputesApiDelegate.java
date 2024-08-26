package dev.vality.disputes.api;

import dev.vality.swag.disputes.api.CreateApiDelegate;
import dev.vality.swag.disputes.api.StatusApiDelegate;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

public interface DisputesApiDelegate extends CreateApiDelegate, StatusApiDelegate {

    @Override
    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }
}
