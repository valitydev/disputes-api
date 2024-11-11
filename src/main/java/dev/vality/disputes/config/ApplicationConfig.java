package dev.vality.disputes.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {

    @Bean
    public ExecutorService disputesThreadPool(@Value("${dispute.batchSize}") int threadPoolSize) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("dispute-exec-%d")
                .setDaemon(true)
                .build();
        return Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    }

    @Bean
    public ExecutorService providerPaymentsThreadPool(@Value("${provider.payments.batchSize}") int threadPoolSize) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("provider-payments-exec-%d")
                .setDaemon(true)
                .build();
        return Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    }
}
