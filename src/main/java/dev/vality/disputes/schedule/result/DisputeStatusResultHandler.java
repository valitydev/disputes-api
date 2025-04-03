package dev.vality.disputes.schedule.result;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.provider.payments.converter.TransactionContextConverter;
import dev.vality.disputes.provider.payments.exception.ProviderCallbackAlreadyExistException;
import dev.vality.disputes.provider.payments.exception.ProviderPaymentsUnexpectedPaymentStatus;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.schedule.converter.DisputeCurrencyConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.util.ErrorFormatter;
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
    private final ProviderPaymentsService providerPaymentsService;
    private final TransactionContextConverter transactionContextConverter;
    private final DisputeCurrencyConverter disputeCurrencyConverter;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

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

    public void handleSucceededResult(Dispute dispute, Long changedAmount) {
        disputesService.finishSucceeded(dispute, changedAmount);
    }

    public void handleSucceededResult(Dispute dispute, DisputeStatusResult result, ProviderData providerData, boolean notify, TransactionInfo transactionInfo) {
        var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
        disputesService.setNextStepToCreateAdjustment(dispute, changedAmount);
        createAdjustment(dispute, providerData, transactionInfo);
        if (notify) {
            callbackNotifier.sendDisputeReadyForCreateAdjustment(dispute);
            mdcTopicProducer.sendReadyForCreateAdjustments(List.of(dispute));
        }
    }

    public void handlePoolingExpired(Dispute dispute) {
        disputesService.setNextStepToPoolingExpired(dispute, ErrorMessage.POOLING_EXPIRED);
        callbackNotifier.sendDisputePoolingExpired(dispute);
        mdcTopicProducer.sendPoolingExpired(dispute);
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
        disputesService.setNextStepToManualPending(dispute, errorMessage);
        callbackNotifier.sendDisputeManualPending(dispute, errorMessage);
        mdcTopicProducer.sendCreated(dispute, DisputeStatus.manual_pending, errorMessage);
    }

    private void createAdjustment(Dispute dispute, ProviderData providerData, TransactionInfo transactionInfo) {
        var transactionContext = transactionContextConverter.convert(dispute.getInvoiceId(), dispute.getPaymentId(), dispute.getProviderTrxId(), providerData, transactionInfo);
        var currency = disputeCurrencyConverter.convert(dispute);
        try {
            var paymentStatusResult = providerPaymentsService.checkPaymentStatusAndSave(transactionContext, currency, providerData, dispute.getAmount());
            if (!paymentStatusResult.isSuccess()) {
                throw new ProviderPaymentsUnexpectedPaymentStatus("Cant do createAdjustment");
            }
        } catch (ProviderCallbackAlreadyExistException ex) {
            log.warn("ProviderCallbackAlreadyExist when handle DisputeStatusResultHandler.createAdjustment", ex);
        }
    }
}
