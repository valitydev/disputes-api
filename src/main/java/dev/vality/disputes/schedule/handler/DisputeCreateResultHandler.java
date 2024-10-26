package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.admin.callback.DefaultCallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class DisputeCreateResultHandler {

    private final DisputeDao disputeDao;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final DefaultRemoteClient defaultRemoteClient;
    private final ProviderDisputeDao providerDisputeDao;
    private final DefaultCallbackNotifier defaultCallbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleSuccessResult(Dispute dispute, DisputeCreatedResult result, ProviderData providerData) {
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
        providerDisputeDao.save(new ProviderDispute(result.getSuccessResult().getProviderDisputeId(), dispute.getId()));
        log.info("Trying to set pending Dispute status {}, {}", dispute, result);
        var isDefaultRouteUrl = defaultRemoteClient.routeUrlEquals(providerData);
        disputeDao.update(dispute.getId(), !isDefaultRouteUrl ? DisputeStatus.pending : DisputeStatus.manual_pending, nextCheckAfter);
        log.debug("Dispute status has been set to pending {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleFailResult(Dispute dispute, DisputeCreatedResult result) {
        var failure = result.getFailResult().getFailure();
        var errorMessage = ErrorFormatter.getErrorMessage(failure);
        if (errorMessage.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
            handleUnexpectedResultMapping(dispute, failure.getCode(), failure.getReason());
        } else {
            log.warn("Trying to set failed Dispute status {}, {}", dispute.getId(), errorMessage);
            disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage, failure.getCode());
            log.debug("Dispute status has been set to failed {}", dispute.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleAlreadyExistResult(Dispute dispute) {
        defaultCallbackNotifier.sendDisputeAlreadyCreated(dispute);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.already_exist_created, "dispute already exist");
        log.info("Trying to set {} Dispute status {}", DisputeStatus.already_exist_created, dispute);
        disputeDao.update(dispute.getId(), DisputeStatus.already_exist_created);
        log.debug("Dispute status has been set to {} {}", DisputeStatus.already_exist_created, dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException e) {
        var errorMessage = e.getErrorDefinition().getErrorReason();
        handleUnexpectedResultMapping(dispute, errorMessage, null);
    }

    private void handleUnexpectedResultMapping(Dispute dispute, String errorCode, String errorDescription) {
        defaultCallbackNotifier.sendDisputeFailedReviewRequired(dispute, errorCode, errorDescription);
        var errorMessage = ErrorFormatter.getErrorMessage(errorCode, errorDescription);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.manual_created, errorMessage);
        log.warn("Trying to set manual_created Dispute status {}, {}", dispute.getId(), errorMessage);
        disputeDao.update(dispute.getId(), DisputeStatus.manual_created, errorMessage);
        log.debug("Dispute status has been set to manual_created {}", dispute.getId());
    }
}
