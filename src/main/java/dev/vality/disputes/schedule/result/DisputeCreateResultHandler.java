package dev.vality.disputes.schedule.result;

import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.client.DefaultRemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.util.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.vality.disputes.constant.ErrorMessage.DEFAULT_DESTINATION;
import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeCreateResultHandler {

    private final DisputesService disputesService;
    private final DefaultRemoteClient defaultRemoteClient;
    private final ProviderDisputeDao providerDisputeDao;

    public void handleRetryLaterResult(Dispute dispute, ProviderData providerData) {
        // дергаем update() чтоб обновить время вызова next_check_after,
        // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
        // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
        disputesService.setNextStepToCreated(dispute, providerData);
    }

    public void handleCheckStatusResult(Dispute dispute, DisputeCreatedResult result, ProviderData providerData) {
        providerDisputeDao.save(result.getSuccessResult().getProviderDisputeId(), dispute);
        var isDefaultRouteUrl = defaultRemoteClient.routeUrlEquals(providerData);
        if (isDefaultRouteUrl) {
            disputesService.setNextStepToManualPending(dispute, null, DEFAULT_DESTINATION);
        } else {
            disputesService.setNextStepToPending(dispute, providerData);
        }
    }

    public void handleSucceededResult(Dispute dispute, Long changedAmount) {
        disputesService.finishSucceeded(dispute, changedAmount);
    }

    public void handleFailedResult(Dispute dispute, DisputeCreatedResult result) {
        var failure = result.getFailResult().getFailure();
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

    public void handleAlreadyExistResult(Dispute dispute) {
        disputesService.setNextStepToAlreadyExist(dispute);
    }

    public void handleUnexpectedResultMapping(Dispute dispute, WRuntimeException ex) {
        var errorReason = ex.getErrorDefinition().getErrorReason();
        var technicalErrorMessage = ErrorFormatter.getErrorMessage(errorReason);
        disputesService.setNextStepToManualPending(dispute, null, technicalErrorMessage);
    }
}
