package dev.vality.disputes.admin.management;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.schedule.core.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminManagementHandler implements AdminManagementServiceSrv.Iface {

    private final AdminManagementDisputesService adminManagementDisputesService;
    private final NotificationService notificationService;

    @Override
    public void cancelPending(CancelParamsRequest cancelParamsRequest) {
        log.info("Got cancelParamsRequest {}", cancelParamsRequest);
        for (var cancelParam : cancelParamsRequest.getCancelParams()) {
            try {
                adminManagementDisputesService.cancelPendingDispute(cancelParam);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle CancelParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish cancelParamsRequest {}", cancelParamsRequest);
    }

    @Override
    public void approvePending(ApproveParamsRequest approveParamsRequest) {
        log.info("Got approveParamsRequest {}", approveParamsRequest);
        for (var approveParam : approveParamsRequest.getApproveParams()) {
            try {
                adminManagementDisputesService.approvePendingDispute(approveParam);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle ApproveParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish approveParamsRequest {}", approveParamsRequest);
    }

    @Override
    public void updatePending(UpdatePendingParamsRequest updatePendingParamsRequest) throws TException {
        log.info("Got updatePendingParamsRequest {}", updatePendingParamsRequest);
        for (var updatePendingParam : updatePendingParamsRequest.getPendingParams()) {
            try {
                adminManagementDisputesService.updatePendingDispute(updatePendingParam);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle UpdatePendingParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish updatePendingParamsRequest {}", updatePendingParamsRequest);
    }

    @Override
    public void bindCreated(BindParamsRequest bindParamsRequest) {
        log.info("Got bindParamsRequest {}", bindParamsRequest);
        for (var bindParam : bindParamsRequest.getBindParams()) {
            try {
                adminManagementDisputesService.bindCreatedDispute(bindParam);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle BindParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish bindParamsRequest {}", bindParamsRequest);
    }

    @Override
    public DisputeResult getDisputes(DisputeParamsRequest disputeParamsRequest) {
        log.info("Got disputeParamsRequest {}", disputeParamsRequest);
        var disputeResult = new DisputeResult(new ArrayList<>());
        for (var disputeParams : disputeParamsRequest.getDisputeParams()) {
            try {
                var dispute = adminManagementDisputesService.getDispute(disputeParams,
                        disputeParamsRequest.isWithAttachments());
                disputeResult.getDisputes().add(dispute);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle DisputeParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish disputeParamsRequest {}", disputeParamsRequest);
        return disputeResult;
    }

    @Override
    public void setPendingForPoolingExpired(
            SetPendingForPoolingExpiredParamsRequest setPendingForPoolingExpiredParamsRequest) {
        log.info("Got setPendingForPoolingExpiredParamsRequest {}", setPendingForPoolingExpiredParamsRequest);
        for (var params : setPendingForPoolingExpiredParamsRequest.getSetPendingForPoolingExpiredParams()) {
            try {
                adminManagementDisputesService.setPendingForPoolingExpiredDispute(params);
            } catch (NotFoundException ex) {
                log.warn("NotFound when handle SetPendingForPoolingExpiredParamsRequest, type={}", ex.getType(), ex);
            }
        }
        log.debug("Finish setPendingForPoolingExpiredParamsRequest {}", setPendingForPoolingExpiredParamsRequest);
    }

    @Override
    public void sendMerchantsNotification(MerchantsNotificationParamsRequest params) {
        log.info("Got sendMerchantsNotification {}", params);
        notificationService.sendMerchantsNotification(params);
        log.debug("Finish sendMerchantsNotification {}", params);
    }
}
