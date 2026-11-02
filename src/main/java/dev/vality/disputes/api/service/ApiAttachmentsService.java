package dev.vality.disputes.api.service;

import dev.vality.disputes.dao.AttachmentDocumentDao;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.domain.tables.pojos.AttachmentDocument;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.disputes.util.AttachmentValidator;
import dev.vality.swag.disputes.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.pending;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAttachmentsService {

    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;
    private final AttachmentDocumentDao attachmentDocumentDao;

    @SneakyThrows(NoSuchAlgorithmException.class)
    public void createAttachments(CreateRequest req, UUID disputeId) {
        log.debug("Trying to save Attachments {}", disputeId);
        for (var attachment : req.getAttachments()) {
            // validate
            AttachmentValidator.validateMimeType(attachment.getData(), attachment.getMimeType());
            var fileId = fileStorageService.saveFile(attachment.getData());
            final var uploadedAt = LocalDateTime.now(ZoneOffset.UTC);
            var fileMeta = new FileMeta(fileId, disputeId, attachment.getMimeType());
            fileMetaDao.save(fileMeta);
            var document = new AttachmentDocument();
            document.setFileId(fileId);
            document.setDisputeId(disputeId);
            document.setInvoiceId(req.getInvoiceId());
            document.setPaymentId(req.getPaymentId());
            document.setSha256(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(attachment.getData())));
            document.setSizeBytes((long) attachment.getData().length);
            document.setMimeType(attachment.getMimeType());
            document.setFileUploadedAt(uploadedAt);
            document.setCreatedAt(uploadedAt);
            document.setUpdatedAt(uploadedAt);
            document.setNextAttemptAfter(uploadedAt);
            document.setAttemptCount(0);
            document.setStatus(pending);
            attachmentDocumentDao.save(document);
        }
        log.debug("Attachments have been saved {}", disputeId);
    }
}
