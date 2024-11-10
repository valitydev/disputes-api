package dev.vality.disputes.api.service;

import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.swag.disputes.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class ApiAttachmentsService {

    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;

    public void createAttachments(CreateRequest req, UUID disputeId) {
        log.debug("Trying to save Attachments {}", disputeId);
        for (var attachment : req.getAttachments()) {
            // validate
            MediaType.valueOf(attachment.getMimeType());
            var fileId = fileStorageService.saveFile(attachment.getData());
            var fileMeta = new FileMeta(fileId, disputeId, attachment.getMimeType());
            fileMetaDao.save(fileMeta);
        }
        log.debug("Attachments have been saved {}", disputeId);
    }
}
