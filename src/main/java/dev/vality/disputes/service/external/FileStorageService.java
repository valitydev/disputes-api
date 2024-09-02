package dev.vality.disputes.service.external;

import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;

public interface FileStorageService {

    String saveFile(CreateRequestAttachmentsInner attachment);

    String generateDownloadUrl(String fileId);

}
