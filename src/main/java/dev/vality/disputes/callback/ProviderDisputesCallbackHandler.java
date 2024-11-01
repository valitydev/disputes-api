package dev.vality.disputes.callback;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderDisputesCallbackHandler implements ProviderDisputesCallbackServiceSrv.Iface {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final DisputeStatusResultHandler disputeStatusResultHandler;

    @Override
    public void createAdjustmentIfPaymentSuccess(DisputeCallbackParams disputeCallbackParams) throws TException {
        log.info("{}", disputeCallbackParams);
    }
}
