package dev.vality.disputes.config.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits for one logical external call made by background processing (created/pending/forgotten disputes and
 * provider payments adjustments). They only ever tighten the timeouts already configured for a client.
 */
@Configuration
@ConfigurationProperties(prefix = "background-processing.remote-call")
@Validated
@Getter
@Setter
public class RemoteCallProperties {

    /**
     * Total time one external operation may take: waiting for a pooled connection, connecting, sending and
     * receiving the response. A composite operation (createDispute + HTTP availability check + fallback
     * createDispute) shares a single budget.
     */
    @NotNull
    @DurationMin(nanos = 1)
    private Duration maxDuration = Duration.ofSeconds(30);
    @NotNull
    @DurationMin(nanos = 1)
    private Duration connectTimeout = Duration.ofSeconds(5);
    @NotNull
    @DurationMin(nanos = 1)
    private Duration poolTimeout = Duration.ofSeconds(5);

}
