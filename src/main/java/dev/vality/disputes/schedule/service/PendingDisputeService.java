package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.InvoicingPaymentStatusPendingException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class PendingDisputeService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final InvoicingService invoicingService;
    private final InvoicePaymentAdjustmentParamsConverter invoicePaymentAdjustmentParamsConverter;
    private final AdjustmentExtractor adjustmentExtractor;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final PollingInfoService pollingInfoService;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getPendingDisputesForUpdateSkipLocked");
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.pending);
        log.debug("PendingDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void callPendingDisputeRemotely(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.pending) {
            log.debug("Dispute locked or wrong status {}", forUpdate);
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        log.debug("Trying to get ProviderDispute {}", dispute);
        var providerDispute = providerDisputeDao.get(dispute.getId());
        if (providerDispute == null) {
            var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
            // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново
            log.error("Trying to set created Dispute status, because createDispute() was not success {}", dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.created, nextCheckAfter);
            log.debug("Dispute status has been set to created {}", dispute);
            return;
        }
        if (pollingInfoService.isDeadline(dispute)) {
            log.error("Trying to set failed Dispute status with POOLING_EXPIRED error reason {}", dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.POOLING_EXPIRED);
            log.debug("Dispute status has been set to failed {}", dispute);
            return;
        }
        log.debug("ProviderDispute has been found {}", dispute);
        var result = remoteClient.checkDisputeStatus(dispute, providerDispute);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> {
                var invoicePayment = getInvoicePayment(dispute);
                if (invoicePayment == null || !invoicePayment.isSetRoute()) {
                    log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute);
                    disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND);
                    log.debug("Dispute status has been set to failed {}", dispute);
                    return;
                }
                var invoicePaymentAdjustment = adjustmentExtractor.searchAdjustmentByDispute(invoicePayment, dispute);
                if (invoicePaymentAdjustment.isPresent()) {
                    var changedAmount = adjustmentExtractor.getChangedAmount(invoicePaymentAdjustment.get(), result);
                    log.info("Trying to set succeeded Dispute status {}, {}", dispute, result);
                    disputeDao.update(dispute.getId(), DisputeStatus.succeeded, changedAmount);
                    log.debug("Dispute status has been set to succeeded {}", dispute);
                    return;
                }
                try {
                    var params = invoicePaymentAdjustmentParamsConverter.convert(dispute, result);
                    var paymentAdjustment = createAdjustment(dispute, params);
                    if (paymentAdjustment == null) {
                        log.error("Trying to set failed Dispute status with INVOICE_NOT_FOUND error reason {}", dispute);
                        disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.INVOICE_NOT_FOUND);
                        log.debug("Dispute status has been set to failed {}", dispute);
                        return;
                    }
                } catch (InvoicingPaymentStatusPendingException e) {
                    // в теории 0%, что сюда попдает выполнение кода, но если попадет, то:
                    // платеж с не финальным статусом будет заблочен для создания корректировок на стороне хелгейта
                    // и тогда диспут будет пулиться, пока платеж не зафиналится,
                    // и тк никакой записи в коде выше нет, то пуллинг не проблема
                    // а запрос в checkDisputeStatus по идемпотентности просто вернет тот же success
                    log.error("Error when hg.createPaymentAdjustment() {}", dispute, e);
                    return;
                }
                log.info("Trying to set succeeded Dispute status {}, {}", dispute, result);
                var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
                disputeDao.update(dispute.getId(), DisputeStatus.succeeded, changedAmount);
                log.debug("Dispute status has been set to succeeded {}", dispute);
            }
            case STATUS_FAIL -> {
                var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
                log.warn("Trying to set failed Dispute status {}, {}", dispute, errorMessage);
                disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
                log.debug("Dispute status has been set to failed {}", dispute);
            }
            case PENDING_SUCCESS -> {
                // дергаем update() чтоб обновить время вызова next_check_after,
                // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
                // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
                var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
                log.info("Trying to set pending Dispute status {}, {}", dispute, result);
                disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
                log.debug("Dispute status has been set to pending {}", dispute);
            }
        }
    }

    private InvoicePaymentAdjustment createAdjustment(Dispute dispute, InvoicePaymentAdjustmentParams params) {
        return invoicingService.createPaymentAdjustment(dispute.getInvoiceId(), dispute.getPaymentId(), params);
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
