package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class DefaultRemoteClientTestConfig {

    @MockBean
    private ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient;

}
