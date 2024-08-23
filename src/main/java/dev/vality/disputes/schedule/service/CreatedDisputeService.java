package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.ProviderRef;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.DisputeCreatedResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.schedule.converter.DisputeParamsConverter;
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
public class CreatedDisputeService {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final ProviderRouting providerRouting;
    private final CreatedAttachmentsService createdAttachmentsService;
    private final DominantService dominantService;
    private final InvoicingService invoicingService;
    private final DisputeParamsConverter disputeParamsConverter;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getCreatedDisputesForUpdateSkipLocked");
        var locked = disputeDao.getCreatedDisputesForUpdateSkipLocked(batchSize);
        log.debug("CreatedDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
    public void callCreateDisputeRemotely(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.created) {
            return;
        }
        var invoicePayment = getInvoicePayment(dispute);
        if (invoicePayment == null || !invoicePayment.isSetRoute()) {
            log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute);
            disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND, null);
            log.debug("Dispute status has been set to failed {}", dispute);
            return;
        }
        var status = invoicePayment.getPayment().getStatus();
        if (!status.isSetCaptured() && !status.isSetCancelled() && !status.isSetFailed()) {
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        var attachments = createdAttachmentsService.getAttachments(dispute);
        if (attachments == null || attachments.isEmpty()) {
            log.error("Trying to set failed Dispute status with NO_ATTACHMENTS error reason {}", dispute);
            disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, ErrorReason.NO_ATTACHMENTS, null);
            log.debug("Dispute status has been set to failed {}", dispute);
            return;
        }
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, terminal.get().getOptions());
        var remoteClient = providerRouting.getConnection(terminal.get().getOptions(), proxy.get().getUrl());
        log.debug("Trying to routed remote provider's createDispute() call {}", dispute);
        var result = remoteClient.createDispute(disputeParams);
        log.debug("Routed remote provider's createDispute() has been called {}", result);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeCreatedResult result) {
        switch (result.getSetField()) {
            case SUCCESS_RESULT -> {
                log.debug("Trying to set pending Dispute status {}, {}", dispute, result);
                providerDisputeDao.save(new ProviderDispute(result.getSuccessResult().getDisputeId(), dispute.getId()));
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.pending, null, null);
                log.debug("Dispute status has been set to pending {}", dispute);
            }
            case FAIL_RESULT -> {
                var errorMessage = TErrorUtil.toStringVal(result.getFailResult().getFailure());
                log.debug("Trying to set failed Dispute status {}, {}", dispute, errorMessage);
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, errorMessage, null);
                log.debug("Dispute status has been set to failed {}", dispute);
            }
        }
    }

    private CompletableFuture<Terminal> getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }

    private CompletableFuture<ProxyDefinition> getProxy(Integer providerId) {
        return dominantService.getProxy(new ProviderRef(providerId));
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
