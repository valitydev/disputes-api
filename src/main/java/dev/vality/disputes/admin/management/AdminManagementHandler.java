package dev.vality.disputes.admin.management;

import dev.vality.disputes.admin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class AdminManagementHandler implements AdminManagementServiceSrv.Iface {

    private final AdminManagementDisputesService adminManagementDisputesService;

    @Override
    public void cancelPending(CancelParamsRequest cancelParamsRequest) throws TException {
        for (var cancelParam : cancelParamsRequest.getCancelParams()) {
            adminManagementDisputesService.cancelPendingDispute(cancelParam);
        }
    }

    @Override
    public void approvePending(ApproveParamsRequest approveParamsRequest) throws TException {
        for (var approveParam : approveParamsRequest.getApproveParams()) {
            adminManagementDisputesService.approvePendingDispute(approveParam);
        }
    }

    @Override
    public void bindCreated(BindParamsRequest bindParamsRequest) throws TException {
        for (var bindParam : bindParamsRequest.getBindParams()) {
            adminManagementDisputesService.bindCreatedDispute(bindParam);
        }
    }

    @Override
    public DisputeResult getDisputes(DisputeParamsRequest disputeParamsRequest) throws TException {
        var disputeResult = new DisputeResult(new ArrayList<>());
        for (var disputeParams : disputeParamsRequest.getDisputeParams()) {
            var dispute = adminManagementDisputesService.getDispute(disputeParams, disputeParamsRequest.isWithAttachments());
            if (dispute != null) {
                disputeResult.getDisputes().add(dispute);
            }
        }
        return disputeResult;
    }
}
