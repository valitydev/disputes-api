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
@SuppressWarnings({"ParameterName", "LineLength"})
public class ApiAttachmentsService {

    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void createAttachments(CreateRequest req, Long disputeId) {
        log.debug("Trying to save Attachments {}", disputeId);
        for (CreateRequestAttachmentsInner attachment : req.getAttachments()) {
            MimeType.valueOf(attachment.getMimeType());
            // http 500
            var fileId = fileStorageService.saveFile(attachment.getData());
            var fileMeta = new FileMeta(fileId, disputeId, attachment.getMimeType());
            log.debug("Trying to save Attachment {}", fileMeta.getFileId());
            fileMetaDao.save(fileMeta);
        }
        log.debug("Attachments have been saved {}", disputeId);
    }
}
