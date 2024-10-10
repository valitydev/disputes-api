package dev.vality.disputes.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.domain_config.RepositoryClientSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.damsel.payment_processing.PartyManagementSrv;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {

    @Bean
    public InvoicingSrv.Iface invoicingClient(
            @Value("${service.invoicing.url}") Resource resource,
            @Value("${service.invoicing.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withAddress(resource.getURI())
                .withNetworkTimeout(networkTimeout)
                .build(InvoicingSrv.Iface.class);
    }

    @Bean
    public RepositoryClientSrv.Iface dominantClient(
            @Value("${service.dominant.url}") Resource resource,
            @Value("${service.dominant.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(RepositoryClientSrv.Iface.class);
    }

    @Bean
    public ArbiterSrv.Iface bouncerClient(
            @Value("${service.bouncer.url}") Resource resource,
            @Value("${service.bouncer.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(ArbiterSrv.Iface.class);
    }

    @Bean
    public TokenAuthenticatorSrv.Iface tokenKeeperClient(
            @Value("${service.tokenKeeper.url}") Resource resource,
            @Value("${service.tokenKeeper.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(TokenAuthenticatorSrv.Iface.class);
    }

    @Bean
    public FileStorageSrv.Iface fileStorageClient(
            @Value("${service.file-storage.url}") Resource resource,
            @Value("${service.file-storage.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(FileStorageSrv.Iface.class);
    }

    @Bean
    public PartyManagementSrv.Iface partyManagementClient(
            @Value("${service.party-management.url}") Resource resource,
            @Value("${service.party-management.networkTimeout}") int timeout
    ) throws IOException {
        return new THSpawnClientBuilder()
                .withAddress(resource.getURI())
                .withNetworkTimeout(timeout)
                .build(PartyManagementSrv.Iface.class);
    }

    @Bean
    public ExecutorService disputesThreadPool(@Value("${dispute.batchSize}") int threadPoolSize) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("dispute-exec-%d")
                .setDaemon(true)
                .build();
        return Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    }
}
