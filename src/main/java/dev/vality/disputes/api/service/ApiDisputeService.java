package dev.vality.disputes.api.service;

import dev.vality.disputes.api.converter.DisputeConverter;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.swag.disputes.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class ApiDisputeService {

    private final DisputeDao disputeDao;
    private final ApiAttachmentsService apiAttachmentsService;
    private final DisputeConverter disputeConverter;

    @Transactional(propagation = Propagation.REQUIRED)
    public Long createDispute(CreateRequest req, PaymentParams paymentParams) {
        log.debug("Start creating Dispute {}", paymentParams);
        var dispute = disputeConverter.convert(paymentParams, req.getAmount(), req.getReason());
        log.debug("Trying to save Dispute {}", dispute);
        // http 500
        var disputeId = disputeDao.save(dispute);
        log.debug("Dispute has been saved {}", dispute);
        apiAttachmentsService.createAttachments(req, disputeId);
        log.debug("Finish creating Dispute {}", dispute);
        return disputeId;
    }

    public Dispute getDispute(String disputeId, String invoiceId, String paymentId) {
        log.debug("Trying to get Dispute, disputeId={}", disputeId);
        // http 404,500
        var dispute = disputeDao.get(Long.parseLong(disputeId), invoiceId, paymentId);
        if (ErrorReason.NO_ATTACHMENTS.equals(dispute.getErrorMessage())
                || ErrorReason.INVOICE_NOT_FOUND.equals(dispute.getErrorMessage())
                || ErrorReason.PAYMENT_NOT_FOUND.equals(dispute.getErrorMessage())) {
            // NO_ATTACHMENTS|... нет резона отдавать наружу по http, тк она не является смысловой для юзера
            // это внутренний флаг, что получили 500 при работе с внутренними данными и лучше создать диспут заново
            // http 404
            throw new NotFoundException(String.format("Dispute not found, disputeId='%s'", disputeId));
        }
        log.debug("Dispute has been found, disputeId={}", disputeId);
        return dispute;
    }
}
