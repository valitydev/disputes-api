package dev.vality.disputes.config;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;

@Configuration
public class HellgateConfig {

    @Bean
    public InvoicingSrv.Iface invoicingClient(
            @Value("${service.invoicing.url}") Resource resource,
            @Value("${service.invoicing.networkTimeout}") int networkTimeout,
            @Qualifier("woodyHttpClient") CloseableHttpClient woodyHttpClient) throws IOException {
        return new THSpawnClientBuilder()
                .withHttpClient(woodyHttpClient)
                .withAddress(resource.getURI())
                .withNetworkTimeout(networkTimeout)
                .build(InvoicingSrv.Iface.class);
    }
}
