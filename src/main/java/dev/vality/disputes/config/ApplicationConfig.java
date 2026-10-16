package dev.vality.disputes.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {

    @Bean
    public ExecutorService createdDisputesThreadPool(
            @Value("${dispute.workers.created.pool-size:${dispute.batchSize}}") int threadPoolSize) {
        return workerPool(threadPoolSize, "dispute-created-exec-%d");
    }

    @Bean
    public ExecutorService pendingDisputesThreadPool(
            @Value("${dispute.workers.pending.pool-size:${dispute.batchSize}}") int threadPoolSize) {
        return workerPool(threadPoolSize, "dispute-pending-exec-%d");
    }

    @Bean
    public ExecutorService forgottenDisputesThreadPool(
            @Value("${dispute.workers.forgotten.pool-size:${dispute.batchSize}}") int threadPoolSize) {
        return workerPool(threadPoolSize, "dispute-forgotten-exec-%d");
    }

    @Bean
    public ExecutorService notificationThreadPool(
            @Value("${dispute.workers.notification.pool-size:${dispute.batchSize}}") int threadPoolSize) {
        return workerPool(threadPoolSize, "dispute-notification-exec-%d");
    }

    @Bean
    public ExecutorService providerPaymentsThreadPool(
            @Value("${provider.payments.worker-pool-size:${provider.payments.batchSize}}") int threadPoolSize) {
        return workerPool(threadPoolSize, "provider-payments-exec-%d");
    }

    @Bean
    @Primary
    public JsonMapper customObjectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }

    private static ExecutorService workerPool(int threadPoolSize, String threadNameFormat) {
        var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(threadNameFormat)
                .setDaemon(true)
                .build();
        return Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    }
}
