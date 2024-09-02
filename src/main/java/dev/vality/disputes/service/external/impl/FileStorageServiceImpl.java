package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.config.properties.FileStorageProperties;
import dev.vality.disputes.exception.FileStorageException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.file.storage.FileNotFound;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.thrift.TException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties fileStorageProperties;
    private final FileStorageSrv.Iface fileStorageClient;
    private final CloseableHttpClient httpClient;

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    @SneakyThrows
    public String saveFile(CreateRequestAttachmentsInner attachment) {
        log.debug("Trying to create new file to file-storage");
        var result = fileStorageClient.createNewFile(Collections.emptyMap(), getTime().toString());
        var fileDataId = result.getFileDataId();
        log.debug("Trying to upload data to s3 with id: {}", fileDataId);
        var requestPut = new HttpPut(result.getUploadUrl());
        var fileName = "dispute-" + UUID.randomUUID();
        var encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        requestPut.setHeader("Content-Disposition", "attachment;filename=" + encode);
        var tempFile = Files.createTempFile(fileName, MediaType.valueOf(attachment.getMimeType()).getSubtype());
        try {
            var data = attachment.getData();
            var outputStream = Files.newOutputStream(tempFile);
            outputStream.write(data);
            outputStream.close();
            var contentType = ContentType.create(attachment.getMimeType(), StandardCharsets.UTF_8);
            requestPut.setEntity(HttpEntities.create(tempFile.toFile(), contentType));
            // execute() делает внутри try-with-resources + закрывает InputStream в EntityUtils.consume(entity)
            httpClient.execute(requestPut, new BasicHttpClientResponseHandler());
        } finally {
            Files.deleteIfExists(tempFile);
        }
        log.debug("File has been successfully uploaded with id: {}", fileDataId);
        return fileDataId;
    }

    @Override
    public String generateDownloadUrl(String fileId) {
        try {
            log.debug("Trying to generate presigned url from file-storage with id: {}", fileId);
            var url = fileStorageClient.generateDownloadUrl(fileId, getTime().toString());
            if (StringUtils.isBlank(url)) {
                throw new NotFoundException(String.format("Presigned url is null, fileId='%s'", fileId));
            }
            log.debug("Presigned url has been generated with id: {}", fileId);
            return url;
        } catch (FileNotFound e) {
            throw new NotFoundException(String.format("File not found, fileId='%s'", fileId), e);
        } catch (TException e) {
            throw new FileStorageException(String.format("Failed to generateDownloadUrl, fileId='%s'", fileId), e);
        }
    }

    private Instant getTime() {
        return LocalDateTime.now(fileStorageProperties.getTimeZone())
                .plusMinutes(fileStorageProperties.getUrlLifeTimeDuration())
                .toInstant(ZoneOffset.UTC);
    }
}
