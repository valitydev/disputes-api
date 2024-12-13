package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.AsyncProperties;
import dev.vality.disputes.service.MdcTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class AsyncMdcConfiguration {

    private final AsyncProperties asyncProperties;

    @Bean("disputesAsyncServiceExecutor")
    public Executor disputesAsyncServiceExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        executor.setThreadNamePrefix("disputesAsyncService-thread-");
        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        executor.initialize();
        return executor;
    }
}
