package dev.vality.disputes.manualparsing;

import dev.vality.disputes.ManualParsingServiceSrv;
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
    public void cancelPending(String disputeId, String cancelReason) throws TException {
        manualParsingDisputesService.cancelPendingDispute(disputeId, cancelReason);
    }

    @Override
    public void approvePending(String disputeId, long changedAmount) throws TException {
        manualParsingDisputesService.approvePendingDispute(disputeId, changedAmount);
    }

    @Override
    public void bindCreated(String disputeId, String providerDisputeId) throws TException {
        manualParsingDisputesService.bindCreatedDispute(disputeId, providerDisputeId);
    }
}
