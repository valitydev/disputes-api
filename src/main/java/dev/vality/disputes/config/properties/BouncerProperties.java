package dev.vality.disputes.config.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "service.bouncer")
public class BouncerProperties {

    @NotEmpty
    private String deploymentId;
    @NotEmpty
    private String ruleSetId;
    @NotEmpty
    private String operationId;

}
