package dev.vality.disputes.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("background-processing")
public class BackgroundProcessingProperties {

    @Valid
    @NotNull
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Retry {
        @NotNull
        @DurationMin(seconds = 1)
        @DurationMax(days = 1)
        private Duration initialDelay = Duration.ofSeconds(5);
        @NotNull
        @DurationMin(seconds = 1)
        @DurationMax(days = 1)
        private Duration maxDelay = Duration.ofMinutes(5);

        @AssertTrue(message = "maxDelay must not be less than initialDelay")
        public boolean isMaxDelayNotLessThanInitialDelay() {
            return initialDelay == null || maxDelay == null || maxDelay.compareTo(initialDelay) >= 0;
        }
    }
}
