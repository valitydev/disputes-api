package dev.vality.disputes.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties("service.candlekeep.registration")
public class DocumentRegistrationProperties {

    @Min(1)
    private int batchSize = 100;
    @Min(1)
    private int maxAttempts = 5;
    @NotNull
    @DurationMin(seconds = 1)
    private Duration retryDelay = Duration.ofMinutes(5);
}
