package dev.vality.disputes.config;

import dev.vality.disputes.remote.BudgetedClientFactory;
import dev.vality.file.storage.FileStorageSrv;
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
            @Value("${service.file-storage.networkTimeout}") int networkTimeout,
            BudgetedClientFactory budgetedClientFactory) throws IOException {
        return budgetedClientFactory.spawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(FileStorageSrv.Iface.class);
    }
}
