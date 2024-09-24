package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import dev.vality.disputes.service.external.DominantService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class RemoteClientTestConfig {

    @MockBean
    private ProviderIfaceBuilder providerIfaceBuilder;
    @MockBean
    private DominantService dominantService;

}
