package dev.vality.disputes.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "service.party-management.cache")
public class PartyManagementCacheProperties {

    private CacheConfig shops;

    @Getter
    @Setter
    public static class CacheConfig {
        private int poolSize;
        private int ttlSec;
    }
}
