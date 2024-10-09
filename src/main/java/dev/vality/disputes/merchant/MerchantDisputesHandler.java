package dev.vality.disputes.merchant;

import dev.vality.disputes.api.DisputesApiDelegate;
import dev.vality.disputes.merchant.converter.CreateRequestConverter;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.Status200Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class MerchantDisputesHandler implements MerchantDisputesServiceSrv.Iface {

    private final CreateRequestConverter createRequestConverter;
    private final DisputesApiDelegate disputesApiDelegate;

    @Override
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) throws TException {
        var createRequest = createRequestConverter.convert(disputeParams);
        var disputeId = disputesApiDelegate.create(getRequestID(), createRequest, false)
                .getBody()
                .getDisputeId();
        return DisputeCreatedResult.successResult(new DisputeCreatedSuccessResult(disputeId));
    }

    @Override
    public DisputeStatusResult checkDisputeStatus(DisputeContext disputeContext) throws DisputeNotFound, TException {
        var response = disputesApiDelegate.status(getRequestID(), disputeContext.getDisputeId(), false).getBody();
        return switch (response.getStatus()) {
            case PENDING -> DisputeStatusResult.statusPending(new DisputeStatusPendingResult());
            case FAILED -> DisputeStatusResult.statusFail(new DisputeStatusFailResult(getErrorMessage(response)));
            case SUCCEEDED -> DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult());
        };
    }

    private String getRequestID() {
        return UUID.randomUUID().toString();
    }

    private String getErrorMessage(Status200Response response) {
        return Optional.ofNullable(response.getReason())
                .map(GeneralError::getMessage)
                .orElse(null);
    }
}
