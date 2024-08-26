package dev.vality.disputes.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "service.adapters.connection")
public class AdaptersConnectionProperties {

    private Integer timeoutSec = 30;
    private Integer poolSize = 10;
    private Integer ttlMin = 1440;
    private ReconnectProperties reconnect;

    @Getter
    @Setter
    public static class ReconnectProperties {
        private int maxAttempts;
        private int initialDelaySec;
    }
}
