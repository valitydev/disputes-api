package dev.vality.disputes.callback;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static dev.vality.disputes.api.service.ApiDisputesService.DISPUTE_PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderDisputesCallbackHandler implements ProviderDisputesCallbackServiceSrv.Iface {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final DisputeStatusResultHandler disputeStatusResultHandler;

    @Override
    public void changeStatus(DisputeCallbackParams disputeCallbackParams) throws TException {
        var transactionContext = disputeCallbackParams.getTransactionContext();
        var disputes = disputeDao.get(transactionContext.getInvoiceId(), transactionContext.getPaymentId());
        var optionalDispute = disputes.stream()
                .filter(d -> DISPUTE_PENDING.contains(d.getStatus()))
                .findFirst();
        if (optionalDispute.isEmpty()) {
            return;
        }
        var dispute = optionalDispute.get();
        var providerDispute = providerDisputeDao.get(dispute.getId());
        if (providerDispute == null
                || !Objects.equals(providerDispute.getProviderDisputeId(), disputeCallbackParams.getProviderDisputeId())) {
            return;
        }
        log.info("ProviderDisputesCallbackHandler {}", disputeCallbackParams);
        var result = disputeCallbackParams.getDisputeStatusResult();
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> disputeStatusResultHandler.handleStatusSuccess(dispute, result);
            case STATUS_FAIL -> disputeStatusResultHandler.handleStatusFail(dispute, result);
        }
    }
}
