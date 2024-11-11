package dev.vality.disputes.api;

import dev.vality.swag.disputes.api.CreateApiDelegate;
import dev.vality.swag.disputes.api.StatusApiDelegate;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.Status200Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@SuppressWarnings({"LineLength"})
public interface DisputesApiDelegate extends CreateApiDelegate, StatusApiDelegate {

    ResponseEntity<Create200Response> create(String requestId, CreateRequest req, boolean checkUserAccessData);

    ResponseEntity<Status200Response> status(String requestId, String disputeId, boolean checkUserAccessData);

    @Override
    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }
}
