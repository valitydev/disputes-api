package dev.vality.disputes.handler;

import dev.vality.disputes.AdminDisputesServiceSrv;
import dev.vality.disputes.service.DisputesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class AdminDisputesHandler implements AdminDisputesServiceSrv.Iface {

    private final DisputesService disputesService;

    @Override
    public void cancelPending(String disputeId, String cancelReason) throws TException {
        disputesService.cancelPendingDispute(disputeId, cancelReason);
    }
}
