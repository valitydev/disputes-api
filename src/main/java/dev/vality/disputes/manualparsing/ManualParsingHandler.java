package dev.vality.disputes.manualparsing;

import dev.vality.disputes.admin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class ManualParsingHandler implements ManualParsingServiceSrv.Iface {

    private final ManualParsingDisputesService manualParsingDisputesService;

    @Override
    public void cancelPending(CancelParamsRequest cancelParamsRequest) throws TException {
        for (var cancelParam : cancelParamsRequest.getCancelParams()) {
            manualParsingDisputesService.cancelPendingDispute(cancelParam);
        }
    }

    @Override
    public void approvePending(ApproveParamsRequest approveParamsRequest) throws TException {
        for (var approveParam : approveParamsRequest.getApproveParams()) {
            manualParsingDisputesService.approvePendingDispute(approveParam);
        }
    }

    @Override
    public void bindCreated(BindParamsRequest bindParamsRequest) throws TException {
        for (BindParams bindParam : bindParamsRequest.getBindParams()) {
            manualParsingDisputesService.bindCreatedDispute(bindParam);
        }
    }
}
