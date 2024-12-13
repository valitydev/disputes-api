package dev.vality.disputes.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Configuration
@ConfigurationProperties(prefix = "async")
@Validated
@Getter
@Setter
public class AsyncProperties {

    @NotNull
    private Integer corePoolSize;
    @NotNull
    private Integer maxPoolSize;
    @NotNull
    private Integer queueCapacity;
}
