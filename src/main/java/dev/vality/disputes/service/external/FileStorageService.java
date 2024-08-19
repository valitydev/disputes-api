package dev.vality.disputes.service.external;

import org.springframework.core.io.Resource;

import java.nio.file.Path;

public interface FileStorageService {

    boolean isFileExists(String fileId);

    Path downloadFile(String fileId);

    String saveFile(Resource file);

}
