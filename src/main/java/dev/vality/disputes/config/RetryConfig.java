package dev.vality.disputes.config;

import dev.vality.dao.DaoException;
import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class RetryConfig {

    private final AdaptersConnectionProperties adaptersConnectionProperties;

    @Bean
    public RetryTemplate retryDbTemplate() {
        var retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(
                new SimpleRetryPolicy(adaptersConnectionProperties.getReconnect().getMaxAttempts(),
                        Collections.singletonMap(DaoException.class, true), true));
        var backoffPolicy = new ExponentialBackOffPolicy();
        backoffPolicy
                .setInitialInterval(TimeUnit.SECONDS.toMillis(
                        adaptersConnectionProperties.getReconnect().getInitialDelaySec()));
        retryTemplate.setBackOffPolicy(backoffPolicy);
        return retryTemplate;
    }
}
