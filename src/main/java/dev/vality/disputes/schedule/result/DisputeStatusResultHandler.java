package dev.vality.disputes.schedule.result;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.util.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeStatusResultHandler {

    private final DisputesService disputesService;
    private final ProviderPaymentsService providerPaymentsService;

    public void handlePendingResult(Dispute dispute, ProviderData providerData) {
        // дергаем update() чтоб обновить время вызова next_check_after,
        // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
        // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
        disputesService.setNextStepToPending(dispute, providerData);
    }

    public void handleFailedResult(Dispute dispute, DisputeStatusResult result) {
        var failure = result.getStatusFail().getFailure();
        var mapping = failure.getCode();
        var providerMessage = ErrorFormatter.getProviderMessage(failure);
        if (mapping.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
            disputesService.setNextStepToManualPending(dispute, providerMessage, null);
        } else {
            disputesService.finishFailedWithMapping(dispute, mapping, providerMessage);
        }
    }

    public void handleFailedResult(Dispute dispute, String technicalErrorMessage) {
        disputesService.finishFailed(dispute, technicalErrorMessage);
    }

    public void handleSucceededResult(Dispute dispute, Long changedAmount) {
        disputesService.finishSucceeded(dispute, changedAmount, null);
    }

    public void handleCreateAdjustmentResult(
            Dispute dispute,
            DisputeStatusResult result,
            ProviderData providerData,
            TransactionInfo transactionInfo,
            String adminMessage) {
        var changedAmount = getChangedAmount(dispute.getAmount(), result);
        providerPaymentsService.createAdjustment(dispute, providerData, transactionInfo);
        disputesService.setNextStepToCreateAdjustment(
                dispute,
                changedAmount,
                result.getStatusSuccess().getProviderMessage().orElse(null),
                adminMessage);
    }

    public void handlePoolingExpired(Dispute dispute) {
        disputesService.setNextStepToPoolingExpired(dispute);
    }

    public void handleProviderDisputeNotFound(Dispute dispute, ProviderData providerData) {
        // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново, должно быть 0% заходов сюда
        disputesService.setNextStepToCreated(dispute, providerData);
    }

    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException ex) {
        var errorReason = ex.getErrorDefinition().getErrorReason();
        var technicalErrorMessage = ErrorFormatter.getErrorMessage(errorReason);
        disputesService.setNextStepToManualPending(dispute, null, technicalErrorMessage);
    }

    private Long getChangedAmount(long amount, DisputeStatusResult paymentStatusResult) {
        return paymentStatusResult.getStatusSuccess().getChangedAmount()
                .filter(changedAmount -> changedAmount != amount)
                .orElse(null);
    }
}
