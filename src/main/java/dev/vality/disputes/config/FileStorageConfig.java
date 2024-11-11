package dev.vality.disputes.config;

import dev.vality.file.storage.FileStorageSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class FileStorageConfig {

    @Bean
    public FileStorageSrv.Iface fileStorageClient(
            @Value("${service.file-storage.url}") Resource resource,
            @Value("${service.file-storage.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(FileStorageSrv.Iface.class);
    }
}
