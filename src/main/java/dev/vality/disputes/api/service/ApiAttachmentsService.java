package dev.vality.disputes.api.service;

import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAttachmentsService {

    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void createAttachments(CreateRequest req, Long disputeId) {
        log.debug("Trying to save Attachments {}", disputeId);
        req.getAttachments().stream()
                .peek(this::validateMimeType)
                .map(attachment -> new FileMeta(
                        // http 500
                        fileStorageService.saveFile(attachment.getData()),
                        disputeId,
                        attachment.getMimeType()))
                .peek(fileMeta -> log.debug("Trying to save Attachment {}", fileMeta.getFileId()))
                // http 500
                .peek(fileMetaDao::save);
        log.debug("Attachments have been saved {}", disputeId);
    }

    private MimeType validateMimeType(CreateRequestAttachmentsInner attachment) {
        // http 400
        return MimeType.valueOf(attachment.getMimeType());
    }
}
