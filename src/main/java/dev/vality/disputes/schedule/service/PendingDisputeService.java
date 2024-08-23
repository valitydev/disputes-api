package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.InvoicingPaymentStatusPendingException;
import dev.vality.disputes.schedule.converter.DisputeContextConverter;
import dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
    private final AdjustmentExtractor adjustmentExtractor;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getPendingDisputesForUpdateSkipLocked");
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.pending);
        log.debug("PendingDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
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
            // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново
            log.error("Trying to set created Dispute status, because createDispute() was not success {}", dispute);
            disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.created, null, null);
            log.debug("Dispute status has been set to created {}", dispute);
            return;
        }
        log.debug("ProviderDispute has been found {}", dispute);
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeContext = disputeContextConverter.convert(providerDispute, terminal.get().getOptions());
        var remoteClient = providerRouting.getConnection(terminal.get().getOptions(), proxy.get().getUrl());
        log.info("Trying to routed remote provider's checkDisputeStatus() call {}", dispute);
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.debug("Routed remote provider's checkDisputeStatus() has been called {}", dispute);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> {
                var invoicePayment = getInvoicePayment(dispute);
                if (invoicePayment == null || !invoicePayment.isSetRoute()) {
                    log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute);
                    disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND, null);
                    log.debug("Dispute status has been set to failed {}", dispute);
                    return;
                }
                var invoicePaymentAdjustment = adjustmentExtractor.searchAdjustmentByDispute(invoicePayment, dispute);
                if (invoicePaymentAdjustment.isPresent()) {
                    var changedAmount = adjustmentExtractor.getChangedAmount(invoicePaymentAdjustment.get(), result);
                    log.info("Trying to set succeeded Dispute status {}, {}", dispute, result);
                    disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.succeeded, null, changedAmount);
                    log.debug("Dispute status has been set to succeeded {}", dispute);
                    return;
                }
                try {
                    var params = invoicePaymentAdjustmentParamsConverter.convert(dispute, result);
                    var paymentAdjustment = createAdjustment(dispute, params);
                    if (paymentAdjustment == null) {
                        log.error("Trying to set failed Dispute status with INVOICE_NOT_FOUND error reason {}", dispute);
                        disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, ErrorReason.INVOICE_NOT_FOUND, null);
                        log.debug("Dispute status has been set to failed {}", dispute);
                        return;
                    }
                } catch (InvoicingPaymentStatusPendingException e) {
                    // платеж с не финальным статусом будет заблочен для создания корректировок на стороне хелгейта
                    log.error("Error when hg.createPaymentAdjustment() {}", dispute, e);
                    return;
                }
                log.info("Trying to set succeeded Dispute status {}, {}", dispute, result);
                var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.succeeded, null, changedAmount);
                log.debug("Dispute status has been set to succeeded {}", dispute);
            }
            case STATUS_FAIL -> {
                var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
                log.warn("Trying to set failed Dispute status {}, {}", dispute, errorMessage);
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

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
