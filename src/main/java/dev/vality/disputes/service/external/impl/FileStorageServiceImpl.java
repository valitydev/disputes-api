package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.config.properties.FileStorageProperties;
import dev.vality.disputes.exception.FileStorageException;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.file.storage.FileStorageSrv;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties fileStorageProperties;
    private final FileStorageSrv.Iface fileStorageClient;
    private final CloseableHttpClient httpClient;

    @Override
    @SneakyThrows
    public Path downloadFile(String fileId) {
        var url = fileStorageClient.generateDownloadUrl(fileId, getTime().toString());
        var inputFile = Files.createTempFile(generateFileName(), "");
        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
            var entity = response.getEntity();
            if (entity != null) {
                try (var outstream = new FileOutputStream(inputFile.toFile())) {
                    entity.writeTo(outstream);
                }
            }
        }
        return inputFile;
    }

    @Override
    @SneakyThrows
    public String saveFile(Resource file) {
        var result = fileStorageClient.createNewFile(Collections.emptyMap(), getTime().toString());
        var requestPut = new HttpPut(result.getUploadUrl());
        requestPut.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(generateFileName(), StandardCharsets.UTF_8));
        requestPut.setEntity(new FileEntity(file.getFile(), ContentType.APPLICATION_OCTET_STREAM));
        var response = httpClient.execute(requestPut);
        checkResponse(result.getFileDataId(), response);
        return result.getFileDataId();
    }

    private void checkResponse(String fileDataId, HttpResponse response) {
        if (response.getCode() != HttpStatus.SC_OK) {
            throw new FileStorageException(String.format(
                    "Failed to upload file, fileDataId='%s', response='%s'", fileDataId, response));
        }
    }

    private Instant getTime() {
        return LocalDateTime.now(fileStorageProperties.getTimeZone())
                .plusMinutes(fileStorageProperties.getUrlLifeTimeDuration())
                .toInstant(ZoneOffset.UTC);
    }

    private String generateFileName() {
        return "dispute_" + Instant.now().toEpochMilli();
    }
}
