package dev.vality.disputes.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "provider.payments.check-status-scheduler")
@Validated
@Getter
@Setter
public class ProviderPaymentsCheckStatusSchedulerProperties {

    @NotNull
    @Positive
    private Integer poolSize;
    @NotBlank
    private String threadNamePrefix;
    @NotNull
    private Boolean awaitTermination;
    @NotNull
    @Positive
    private Integer awaitTerminationPeriodSec;

}
