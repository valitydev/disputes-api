package dev.vality.disputes.service.external;

public interface FileStorageService {

    String saveFile(byte[] data);

    String generateDownloadUrl(String fileId);

}
