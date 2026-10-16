package dev.vality.disputes.config;

import dev.vality.damsel.domain_config_v2.RepositoryClientSrv;
import dev.vality.disputes.remote.BudgetedClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class DominantConfig {

    @Bean
    public RepositoryClientSrv.Iface dominantClient(
            @Value("${service.dominant.url}") Resource resource,
            @Value("${service.dominant.networkTimeout}") int networkTimeout,
            BudgetedClientFactory budgetedClientFactory) throws IOException {
        return budgetedClientFactory.spawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(RepositoryClientSrv.Iface.class);
    }
}
