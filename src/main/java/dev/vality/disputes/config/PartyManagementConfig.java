package dev.vality.disputes.config;

import dev.vality.damsel.payment_processing.PartyManagementSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class PartyManagementConfig {

    @Bean
    public PartyManagementSrv.Iface partyManagementClient(
            @Value("${service.party-management.url}") Resource resource,
            @Value("${service.party-management.networkTimeout}") int timeout) throws IOException {
        return new THSpawnClientBuilder()
                .withAddress(resource.getURI())
                .withNetworkTimeout(timeout)
                .build(PartyManagementSrv.Iface.class);
    }
}
