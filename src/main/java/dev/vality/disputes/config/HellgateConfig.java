package dev.vality.disputes.config;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.remote.BudgetedClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class HellgateConfig {

    @Bean
    public InvoicingSrv.Iface invoicingClient(
            @Value("${service.invoicing.url}") Resource resource,
            @Value("${service.invoicing.networkTimeout}") int networkTimeout,
            BudgetedClientFactory budgetedClientFactory) throws IOException {
        return budgetedClientFactory.spawnClientBuilder()
                .withAddress(resource.getURI())
                .withNetworkTimeout(networkTimeout)
                .build(InvoicingSrv.Iface.class);
    }
}
