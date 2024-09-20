package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class DisputeStatusResultHandler {

    private final DisputeDao disputeDao;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusPending(Dispute dispute, DisputeStatusResult result) {
        // дергаем update() чтоб обновить время вызова next_check_after,
        // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
        // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
        var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
        log.info("Trying to set pending Dispute status {}, {}", dispute, result);
        disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
        log.debug("Dispute status has been set to pending {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusFail(Dispute dispute, DisputeStatusResult result) {
        var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
        log.warn("Trying to set failed Dispute status {}, {}", dispute.getId(), errorMessage);
        disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
        log.debug("Dispute status has been set to failed {}", dispute.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleStatusSuccess(Dispute dispute, DisputeStatusResult result) {
        var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
        log.info("Trying to set create_adjustment Dispute status {}, {}", dispute, result);
        disputeDao.update(dispute.getId(), DisputeStatus.create_adjustment, changedAmount);
        log.debug("Dispute status has been set to create_adjustment {}", dispute.getId());
    }
}
