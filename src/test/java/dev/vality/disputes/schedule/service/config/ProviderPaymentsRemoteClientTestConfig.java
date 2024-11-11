package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class ProviderPaymentsRemoteClientTestConfig {

    @MockBean
    private ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

}
