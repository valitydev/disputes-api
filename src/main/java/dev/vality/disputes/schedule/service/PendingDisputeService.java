package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.converter.DisputeContextConverter;
import dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class PendingDisputeService {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final ProviderRouting providerRouting;
    private final DominantService dominantService;
    private final InvoicingService invoicingService;
    private final DisputeContextConverter disputeContextConverter;
    private final InvoicePaymentAdjustmentParamsConverter invoicePaymentAdjustmentParamsConverter;
    private final RetryTemplate retryDbTemplate;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getPendingDisputesForUpdateSkipLocked");
        var locked = disputeDao.getPendingDisputesForUpdateSkipLocked(batchSize);
        log.debug("PendingDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
    public void callPendingDisputeRemotely(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.pending) {
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        log.debug("Trying to get ProviderDispute {}", dispute);
        var providerDispute = providerDisputeDao.get(dispute.getId());
        if (providerDispute == null) {
            log.error("Trying to set created Dispute status {}", dispute);
            // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново
            disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.created, null, null);
            log.debug("Dispute status has been set to created {}", dispute);
            return;
        }
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        log.debug("ProviderDispute has been found {}", dispute);
        var disputeContext = disputeContextConverter.convert(providerDispute, terminal.get().getOptions());
        var remoteClient = providerRouting.getConnection(terminal.get().getOptions(), proxy.get().getUrl());
        log.debug("Trying to routed remote provider's checkDisputeStatus() call {}", dispute);
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.debug("Routed remote provider's checkDisputeStatus() has been called {}", result);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> {
                var params = invoicePaymentAdjustmentParamsConverter.convert(dispute, result);
                var paymentAdjustment = createAdjustment(dispute, params);
                if (paymentAdjustment == null) {
                    log.error("Trying to set failed Dispute status with INVOICE_NOT_FOUND error reason {}", dispute);
                    disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, ErrorReason.INVOICE_NOT_FOUND, null);
                    log.debug("Dispute status has been set to failed {}", dispute);
                    return;
                }
                log.debug("Trying to set succeeded Dispute status {}, {}", dispute, result);
                retryDbTemplate.execute(c -> disputeDao.changeDisputeStatus(
                        dispute.getId(),
                        DisputeStatus.succeeded,
                        null,
                        result.getStatusSuccess().getChangedAmount().orElse(null)));
                log.debug("Dispute status has been set to succeeded {}", dispute);
            }
            case STATUS_FAIL -> {
                var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
                log.debug("Trying to set failed Dispute status {}, {}", dispute, errorMessage);
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, errorMessage, null);
                log.debug("Dispute status has been set to failed {}", dispute);
            }
        }
    }

    private CompletableFuture<ProxyDefinition> getProxy(Integer providerId) {
        return dominantService.getProxy(new ProviderRef(providerId));
    }

    private CompletableFuture<Terminal> getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }

    private InvoicePaymentAdjustment createAdjustment(Dispute dispute, InvoicePaymentAdjustmentParams params) {
        return invoicingService.createPaymentAdjustment(dispute.getInvoiceId(), dispute.getPaymentId(), params);
    }
}
