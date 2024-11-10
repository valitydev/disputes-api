package dev.vality.disputes.config;

import dev.vality.disputes.admin.AdminCallbackServiceSrv;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class TgBotConfig {

    @Bean
    public ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient(
            @Value("${service.disputes-tg-bot.provider.url}") Resource resource,
            @Value("${service.disputes-tg-bot.provider.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(ProviderDisputesServiceSrv.Iface.class);
    }

    @Bean
    public AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient(
            @Value("${service.disputes-tg-bot.admin.url}") Resource resource,
            @Value("${service.disputes-tg-bot.admin.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(AdminCallbackServiceSrv.Iface.class);
    }
}
