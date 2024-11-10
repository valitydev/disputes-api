package dev.vality.disputes.config;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class AccessConfig {

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
}
