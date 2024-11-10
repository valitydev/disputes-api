package dev.vality.disputes.schedule.result;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputeCreateResultHandler {

    private final DisputesService disputesService;
    private final DefaultRemoteClient defaultRemoteClient;
    private final ProviderDisputeDao providerDisputeDao;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    public void handleSucceededResult(Dispute dispute, DisputeCreatedResult result, ProviderData providerData) {
        providerDisputeDao.save(result.getSuccessResult().getProviderDisputeId(), dispute);
        var isDefaultRouteUrl = defaultRemoteClient.routeUrlEquals(providerData);
        if (isDefaultRouteUrl) {
            disputesService.setNextStepToManualPending(dispute, ErrorMessage.NEXT_STEP_AFTER_DEFAULT_REMOTE_CLIENT_CALL);
        } else {
            disputesService.setNextStepToPending(dispute, providerData);
        }
    }

    public void handleFailedResult(Dispute dispute, DisputeCreatedResult result) {
        var failure = result.getFailResult().getFailure();
        var errorMessage = ErrorFormatter.getErrorMessage(failure);
        if (errorMessage.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
            handleUnexpectedResultMapping(dispute, failure.getCode(), failure.getReason());
        } else {
            disputesService.finishFailedWithMapping(dispute, errorMessage, failure);
        }
    }

    public void handleFailedResult(Dispute dispute, String errorMessage) {
        disputesService.finishFailed(dispute, errorMessage);
    }

    public void handleAlreadyExistResult(Dispute dispute) {
        callbackNotifier.sendDisputeAlreadyCreated(dispute);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.already_exist_created, "dispute already exist");
        disputesService.setNextStepToAlreadyExist(dispute);
    }

    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException ex) {
        var errorMessage = ex.getErrorDefinition().getErrorReason();
        handleUnexpectedResultMapping(dispute, errorMessage, null);
    }

    private void handleUnexpectedResultMapping(Dispute dispute, String errorCode, String errorDescription) {
        callbackNotifier.sendDisputeFailedReviewRequired(dispute, errorCode, errorDescription);
        var errorMessage = ErrorFormatter.getErrorMessage(errorCode, errorDescription);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.manual_created, errorMessage);
        disputesService.setNextStepToManualCreated(dispute, errorMessage);
    }
}
