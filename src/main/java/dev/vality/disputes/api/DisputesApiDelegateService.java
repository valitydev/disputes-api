package dev.vality.disputes.api;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.model.PaymentParams;
import dev.vality.disputes.security.AccessService;
import dev.vality.disputes.service.PaymentParamsBuilder;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.swag.disputes.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class DisputesApiDelegateService implements DisputesApiDelegate {

    private final AccessService accessService;
    private final PaymentParamsBuilder paymentParamsBuilder;
    private final FileStorageService fileStorageService;
    private final DisputeDao disputeDao;

    @Override
    public ResponseEntity<Create200Response> create(
            String xRequestID,
            String invoiceId,
            String paymentId,
            List<@Valid CreateRequestAttachmentsInner> attachments,
            Amount amount,
            String reason) {
        var accessData = accessService.buildAccessData(invoiceId, paymentId);
        accessService.checkUserAccess(accessData);
        var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
        var files = new ArrayList<FileMeta>();
        for (var attachment : attachments) {
            var fileId = fileStorageService.saveFile(attachment.getAttachment());
            files.add(new FileMeta(fileId, null, attachment.getName(), attachment.getMimeType()));
        }
        var disputeId = disputeDao.saveDispute(getDispute(paymentParams, amount, reason));
        for (var file : files) {
            disputeDao.attachFile(disputeId, file);
        }
        return ResponseEntity.ok(new Create200Response(String.valueOf(disputeId)));
    }

    @Override
    public ResponseEntity<Status200Response> status(
            String xRequestID,
            String invoiceId,
            String paymentId,
            String disputeId) {
        var accessData = accessService.buildAccessData(invoiceId, paymentId);
        accessService.checkUserAccess(accessData);
        var dispute = disputeDao.getDispute(Long.parseLong(disputeId));
        var status = switch (dispute.getStatus()) {
            case pending -> Status200Response.StatusEnum.PENDING;
            case succeeded -> Status200Response.StatusEnum.SUCCEEDED;
            case failed -> Status200Response.StatusEnum.FAILED;
            default -> throw new IllegalArgumentException();
        };
        var body = new Status200Response();
        body.setStatus(status);
        if (!StringUtils.isBlank(dispute.getErrorMessage())) {
            body.setReason(new GeneralError(dispute.getErrorMessage()));
        }
        if (dispute.getChangedAmount() != null) {
            body.setChangedAmount(new Amount(dispute.getChangedAmount()));
        }
        return ResponseEntity.ok(body);
    }

    private Dispute getDispute(PaymentParams paymentParams, Amount amount, String reason) {
        var dispute = new Dispute();
        dispute.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        dispute.setInvoiceId(paymentParams.getInvoiceId());
        dispute.setPaymentId(paymentParams.getPaymentId());
        dispute.setProviderId(paymentParams.getProviderId());
        dispute.setTerminalId(paymentParams.getTerminalId());
        dispute.setAmount(Optional.ofNullable(amount).map(Amount::getAmount).orElse(null));
        dispute.setCurrency(Optional.ofNullable(amount).map(Amount::getCurrency).orElse(null));
        dispute.setReason(reason);
        return dispute;
    }
}
