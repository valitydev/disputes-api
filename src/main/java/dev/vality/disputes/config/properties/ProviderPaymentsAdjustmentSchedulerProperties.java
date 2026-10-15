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
@ConfigurationProperties(prefix = "provider.payments.adjustment-scheduler")
@Validated
@Getter
@Setter
public class ProviderPaymentsAdjustmentSchedulerProperties {

    @NotNull
    @Positive
    private Integer poolSize = 1;
    @NotBlank
    private String threadNamePrefix = "provider-payments-adjustment-scheduler-";
    @NotNull
    private Boolean awaitTermination = true;
    @NotNull
    @Positive
    private Integer awaitTerminationPeriodSec = 30;

}
