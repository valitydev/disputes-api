package dev.vality.disputes.schedule.service;

import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.service.external.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentsService {

    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;

    public List<Attachment> getAttachments(Dispute dispute) {
        log.debug("Trying to get Attachments {}", dispute);
        try {
            var attachments = new ArrayList<Attachment>();
            for (FileMeta fileMeta : fileMetaDao.getDisputeFiles(dispute.getId())) {
                var attachment = new Attachment();
                attachment.setSourceUrl(fileStorageService.generateDownloadUrl(fileMeta.getFileId()));
                attachment.setMimeType(fileMeta.getMimeType());
                attachments.add(attachment);
            }
            log.debug("Attachments have been found {}", dispute);
            return attachments;
        } catch (NullPointerException | NotFoundException e) {
            // закрываем диспут с фейлом если получили не преодолимый отказ внешних шлюзов с ключевыми данными
            return null;
        }
    }
}
