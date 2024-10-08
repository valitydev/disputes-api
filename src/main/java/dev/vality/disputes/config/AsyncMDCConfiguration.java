package dev.vality.disputes.config;

import dev.vality.disputes.service.MDCTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@SuppressWarnings("AbbreviationAsWordInName")
public class AsyncMDCConfiguration {

    @Bean("disputesAsyncServiceExecutor")
    public Executor disputesAsyncServiceExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MDCTaskDecorator());
        executor.initialize();
        executor.setThreadNamePrefix("disputesAsyncService-thread-");
        return executor;
    }
}
