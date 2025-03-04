package dev.vality.disputes.merchant;

import dev.vality.disputes.api.DisputesApiDelegate;
import dev.vality.disputes.merchant.converter.CreateRequestConverter;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.Status200Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class MerchantDisputesHandler implements MerchantDisputesServiceSrv.Iface {

    private final CreateRequestConverter createRequestConverter;
    private final DisputesApiDelegate disputesApiDelegate;

    @Override
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) {
        log.info("Got DisputeParams {}", disputeParams);
        var createRequest = createRequestConverter.convert(disputeParams);
        var disputeId = disputesApiDelegate.create(createRequest, false)
                .getBody()
                .getDisputeId();
        log.info("Finish DisputeParams {}", disputeParams);
        return DisputeCreatedResult.successResult(new DisputeCreatedSuccessResult(disputeId));
    }

    @Override
    public DisputeStatusResult checkDisputeStatus(DisputeContext disputeContext) {
        log.info("Got DisputeContext {}", disputeContext);
        var response = disputesApiDelegate.status(disputeContext.getDisputeId(), false).getBody();
        log.info("Finish DisputeContext {}", disputeContext);
        return switch (response.getStatus()) {
            case PENDING -> DisputeStatusResult.statusPending(new DisputeStatusPendingResult());
            case FAILED -> DisputeStatusResult.statusFail(
                    new DisputeStatusFailResult().setMapping(getMapping(response)));
            case SUCCEEDED -> DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult());
        };
    }

    private String getMapping(Status200Response response) {
        return Optional.ofNullable(response.getReason())
                .map(GeneralError::getMessage)
                .orElse(null);
    }
}
