package dev.vality.disputes.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "service.file-storage")
public class FileStorageProperties {

    private Resource url;
    private int clientTimeout;
    private Long urlLifeTimeDuration;
    private ZoneId timeZone;

}
