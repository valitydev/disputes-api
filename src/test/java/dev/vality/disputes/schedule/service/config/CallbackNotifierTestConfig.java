package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.admin.AdminCallbackServiceSrv;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class CallbackNotifierTestConfig {

    @MockBean
    private AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient;

}
