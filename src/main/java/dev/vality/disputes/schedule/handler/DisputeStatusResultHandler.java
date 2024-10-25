package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.admin.callback.DefaultCallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class DisputeStatusResultHandler {

    private final DisputeDao disputeDao;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final DefaultCallbackNotifier defaultCallbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusPending(Dispute dispute, DisputeStatusResult result, Map<String, String> options) {
        // дергаем update() чтоб обновить время вызова next_check_after,
        // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
        // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, options);
        log.info("Trying to set pending Dispute status {}, {}", dispute, result);
        disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
        log.debug("Dispute status has been set to pending {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusFail(Dispute dispute, DisputeStatusResult result) {
        var errorMessage = ErrorFormatter.getErrorMessage(result.getStatusFail().getFailure());
        if (errorMessage.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
            handleUnexpectedResultMapping(dispute, result.getStatusFail().getFailure().getCode(), result.getStatusFail().getFailure().getReason());
        } else {
            log.warn("Trying to set failed Dispute status {}, {}", dispute.getId(), errorMessage);
            disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
            log.debug("Dispute status has been set to failed {}", dispute.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusSuccess(Dispute dispute, DisputeStatusResult result) {
        defaultCallbackNotifier.sendDisputeReadyForCreateAdjustment(List.of(dispute));
        mdcTopicProducer.sendReadyForCreateAdjustments(List.of(dispute));
        var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
        log.info("Trying to set create_adjustment Dispute status {}, {}", dispute, result);
        disputeDao.update(dispute.getId(), DisputeStatus.create_adjustment, changedAmount);
        log.debug("Dispute status has been set to create_adjustment {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handlePoolingExpired(Dispute dispute) {
        defaultCallbackNotifier.sendDisputePoolingExpired(dispute);
        mdcTopicProducer.sendPoolingExpired(dispute);
        log.error("Trying to set manual_pending Dispute status with POOLING_EXPIRED error reason {}", dispute.getId());
        disputeDao.update(dispute.getId(), DisputeStatus.manual_pending, ErrorReason.POOLING_EXPIRED);
        log.debug("Dispute status has been set to manual_pending {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException e) {
        var errorMessage = e.getErrorDefinition().getErrorReason();
        handleUnexpectedResultMapping(dispute, errorMessage, null);
    }

    private void handleUnexpectedResultMapping(Dispute dispute, String errorCode, String errorDescription) {
        defaultCallbackNotifier.sendDisputeFailedReviewRequired(dispute, errorCode, errorDescription);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.manual_pending);
        var errorMessage = ErrorFormatter.getErrorMessage(errorCode, errorDescription);
        log.warn("Trying to set manual_pending Dispute status {}, {}", dispute.getId(), errorMessage);
        disputeDao.update(dispute.getId(), DisputeStatus.manual_pending, errorMessage);
        log.debug("Dispute status has been set to manual_pending {}", dispute.getId());
    }
}
