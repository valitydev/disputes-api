package dev.vality.disputes.api.service;

import dev.vality.disputes.api.converter.DisputeConverter;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.swag.disputes.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.exception.NotFoundException.Type;
import static dev.vality.disputes.service.DisputesService.DISPUTE_PENDING_STATUSES;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ApiDisputesService {

    private final DisputeDao disputeDao;
    private final ApiAttachmentsService apiAttachmentsService;
    private final DisputeConverter disputeConverter;

    public Optional<Dispute> checkExistBeforeCreate(String invoiceId, String paymentId) {
        log.debug("Trying to checkExistBeforeCreate() Dispute, invoiceId={}", invoiceId);
        var disputes = disputeDao.get(invoiceId, paymentId);
        var first = disputes.stream()
                .filter(dispute -> DISPUTE_PENDING_STATUSES.contains(dispute.getStatus()))
                .findFirst();
        log.debug("Done checkExistBeforeCreate(), invoiceId={}", invoiceId);
        return first;
    }

    @Transactional
    public UUID createDispute(CreateRequest req, PaymentParams paymentParams) {
        log.info("Start creating Dispute {}", paymentParams);
        var dispute = disputeConverter.convert(paymentParams, req.getAmount(), req.getReason());
        var disputeId = disputeDao.save(dispute);
        apiAttachmentsService.createAttachments(req, disputeId);
        log.debug("Finish creating Dispute {}", dispute);
        return disputeId;
    }

    public Dispute getDispute(String disputeId) {
        log.debug("Trying to get Dispute, disputeId={}", disputeId);
        var dispute = Optional.ofNullable(parseFormat(disputeId))
                .map(disputeDao::get)
                .filter(d -> !(ErrorMessage.NO_ATTACHMENTS.equals(d.getErrorMessage())
                        || ErrorMessage.INVOICE_NOT_FOUND.equals(d.getErrorMessage())
                        || ErrorMessage.PAYMENT_NOT_FOUND.equals(d.getErrorMessage())
                        || ErrorMessage.PAYMENT_STATUS_RESTRICTIONS.equals(d.getErrorMessage())))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, disputeId='%s'", disputeId), Type.DISPUTE));
        log.debug("Dispute has been found, disputeId={}", disputeId);
        return dispute;
    }

    private UUID parseFormat(String disputeId) {
        try {
            return UUID.fromString(disputeId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
