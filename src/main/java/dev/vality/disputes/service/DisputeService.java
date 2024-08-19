package dev.vality.disputes.service;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.*;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.RoutingException;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"VariableDeclarationUsageDistance", "LineLength"})
public class DisputeService {

    private static final String DISPUTES_URL_POSTFIX_DEFAULT = "disputes";
    private static final String OPTION_DISPUTES_URL_FIELD_NAME = "disputes_url";

    private final DisputeDao disputeDao;
    private final ProviderRouting providerRouting;
    private final DominantService dominantService;
    private final FileStorageService fileStorageService;
    private final InvoicingService invoicingService;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getCreatedDisputes(int batchSize) {
        return disputeDao.getCreatedDisputes(batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputes(int batchSize) {
        return disputeDao.getPendingDisputes(batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
    public void createDispute(Dispute dispute) {
        var forUpdate = disputeDao.getDisputeDoUpdateSkipLocked(dispute.getId());
        if (forUpdate != null && forUpdate.getStatus() == DisputeStatus.created) {
            var terminal = dominantService.getTerminal(new TerminalRef(Integer.parseInt(dispute.getTerminalId())));
            var provider = dominantService.getProvider(new ProviderRef(Integer.parseInt(dispute.getProviderId())));
            var proxy = dominantService.getProxy(provider.get().getProxy().getRef());
            var transactionContext = new TransactionContext();
            transactionContext.setProviderTrxId(dispute.getProviderTrxId());
            transactionContext.setTerminalOptions(terminal.get().getOptions());
            transactionContext.setInvoiceId(dispute.getInvoiceId());
            transactionContext.setPaymentId(dispute.getPaymentId());
            var disputeParams = new DisputeParams();
            disputeParams.setTransactionContext(transactionContext);
            var attachments = new ArrayList<Attachment>();
            var disputeFiles = disputeDao.getDisputeFiles(dispute.getId());
            for (FileMeta disputeFile : disputeFiles) {
                var file = fileStorageService.downloadFile(disputeFile.getFileId());
                var attachment = new Attachment();
                attachment.setSource(Files.readAllBytes(file));
                attachment.setMimeType(disputeFile.getMimeType());
                attachment.setName(disputeFile.getFilename());
                attachments.add(attachment);
            }
            disputeParams.setAttachments(attachments);
            var amount = new Amount();
            amount.setValue(dispute.getAmount());
            amount.setCurrency(dispute.getCurrency());
            disputeParams.setAmount(amount);
            disputeParams.setReason(dispute.getReason());
            var route = providerRouting.getConnection(getRouteUrl(terminal.get().getOptions(), proxy.get().getUrl()));
            var result = route.createDispute(disputeParams);
            finishTask(dispute, result);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SneakyThrows
    public void pendingDispute(Dispute dispute) {
        var forUpdate = disputeDao.getDisputeDoUpdateSkipLocked(dispute.getId());
        if (forUpdate != null && forUpdate.getStatus() == DisputeStatus.pending) {
            var terminal = dominantService.getTerminal(new TerminalRef(Integer.parseInt(dispute.getTerminalId())));
            var provider = dominantService.getProvider(new ProviderRef(Integer.parseInt(dispute.getProviderId())));
            var proxy = dominantService.getProxy(provider.get().getProxy().getRef());
            var providerDispute = disputeDao.getProviderDispute(dispute.getId());
            var disputeContext = new DisputeContext();
            disputeContext.setDisputeId(providerDispute.getProviderDisputeId());
            disputeContext.setTerminalOptions(terminal.get().getOptions());
            var route = providerRouting.getConnection(getRouteUrl(terminal.get().getOptions(), proxy.get().getUrl()));
            var result = route.checkDisputeStatus(disputeContext);
            finishTask(dispute, result);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeCreatedResult result) {
        switch (result.getSetField()) {
            case SUCCESS_RESULT -> {
                disputeDao.saveProviderDispute(
                        new ProviderDispute(result.getSuccessResult().getDisputeId(), dispute.getId()));
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.pending, null, null);
            }
            case FAIL_RESULT -> {
                var errorMessage = TErrorUtil.toStringVal(result.getFailResult().getFailure());
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, errorMessage, null);
            }
            default -> {
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> {
                var params = new InvoicePaymentAdjustmentParams();
                params.setReason(Optional.ofNullable(dispute.getReason()).orElse("dispute"));
                var captured = new InvoicePaymentCaptured();
                var changedAmount = result.getStatusSuccess().getChangedAmount();
                if (changedAmount.isPresent()) {
                    CurrencyRef currency;
                    if (changedAmount.get().getCurrency().isPresent()) {
                        currency = new CurrencyRef(changedAmount.get().getCurrency().get());
                    } else {
                        currency = new CurrencyRef(dispute.getCurrency());
                    }
                    var cost = new Cash(changedAmount.get().getValue(), currency);
                    captured.setCost(cost);
                }
                var scenario = InvoicePaymentAdjustmentScenario.status_change(
                        new InvoicePaymentAdjustmentStatusChange(InvoicePaymentStatus.captured(captured)));
                params.setScenario(scenario);
                invoicingService.createPaymentAdjustment(dispute.getInvoiceId(), dispute.getPaymentId(), params);
                disputeDao.changeDisputeStatus(
                        dispute.getId(),
                        DisputeStatus.succeeded,
                        null,
                        changedAmount.map(Amount::getValue).orElse(null));
            }
            case STATUS_FAIL -> {
                var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
                disputeDao.changeDisputeStatus(dispute.getId(), DisputeStatus.failed, errorMessage, null);
            }
            default -> {
            }
        }
    }

    private String getRouteUrl(Map<String, String> options, String defaultProviderUrl) {
        var url = options.get(OPTION_DISPUTES_URL_FIELD_NAME);
        if (ObjectUtils.isEmpty(url)) {
            url = createDefaultRouteUrl(defaultProviderUrl);
        }
        return url;
    }

    private String createDefaultRouteUrl(String defaultProviderUrl) {
        log.debug("Creating url by appending postfix");
        try {
            var validUri = new URL(defaultProviderUrl).toURI();
            return UriComponentsBuilder.fromUri(validUri)
                    .pathSegment(DISPUTES_URL_POSTFIX_DEFAULT)
                    .encode()
                    .build()
                    .toUriString();
        } catch (Exception e) {
            throw new RoutingException("Unable to create default provider url: ", e);
        }
    }
}
