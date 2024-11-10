package dev.vality.disputes.schedule.result;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputeStatusResultHandler {

    private final DisputesService disputesService;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;
    private final ProviderPaymentsCallbackServiceSrv.Iface providerPaymentsCallbackHandler;

    public void handlePendingResult(Dispute dispute, ProviderData providerData) {
        // дергаем update() чтоб обновить время вызова next_check_after,
        // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
        // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
        disputesService.setNextStepToPending(dispute, providerData);
    }

    public void handleFailedResult(Dispute dispute, DisputeStatusResult result) {
        var failure = result.getStatusFail().getFailure();
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

    public void handleSucceededResult(Dispute dispute, DisputeStatusResult result, boolean notify) {
        if (notify) {
            callbackNotifier.sendDisputeReadyForCreateAdjustment(dispute);
            mdcTopicProducer.sendReadyForCreateAdjustments(List.of(dispute));
        }
        var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
        disputesService.setNextStepToCreateAdjustment(dispute, changedAmount);
        createAdjustmentWhenFailedPaymentSuccess(dispute);
    }

    public void handlePoolingExpired(Dispute dispute) {
        callbackNotifier.sendDisputePoolingExpired(dispute);
        mdcTopicProducer.sendPoolingExpired(dispute);
        disputesService.setNextStepToManualPending(dispute, ErrorMessage.POOLING_EXPIRED);
    }

    public void handleProviderDisputeNotFound(Dispute dispute, ProviderData providerData) {
        // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново, должно быть 0% заходов сюда
        disputesService.setNextStepToCreated(dispute, providerData);
    }

    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException ex) {
        var errorMessage = ex.getErrorDefinition().getErrorReason();
        handleUnexpectedResultMapping(dispute, errorMessage, null);
    }

    private void handleUnexpectedResultMapping(Dispute dispute, String errorCode, String errorDescription) {
        var errorMessage = ErrorFormatter.getErrorMessage(errorCode, errorDescription);
        callbackNotifier.sendDisputeFailedReviewRequired(dispute, errorCode, errorDescription);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.manual_pending, errorMessage);
        disputesService.setNextStepToManualPending(dispute, errorMessage);
    }

    private void createAdjustmentWhenFailedPaymentSuccess(Dispute dispute) {
        try {
            providerPaymentsCallbackHandler.createAdjustmentWhenFailedPaymentSuccess(
                    new ProviderPaymentsCallbackParams()
                            .setInvoiceId(dispute.getInvoiceId())
                            .setPaymentId(dispute.getPaymentId()));
        } catch (Throwable ex) {
            log.error("Received exception while DisputeStatusResultHandler.createAdjustmentWhenFailedPaymentSuccess", ex);
        }
    }
}
