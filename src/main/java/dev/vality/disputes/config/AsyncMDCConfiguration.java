package dev.vality.disputes.config;

import dev.vality.disputes.service.MDCTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@SuppressWarnings("AbbreviationAsWordInName")
public class AsyncMDCConfiguration {

    @Bean("dominantAsyncServiceExecutor")
    public Executor dominantAsyncServiceExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MDCTaskDecorator());
        executor.initialize();
        executor.setThreadNamePrefix("dominantAsyncService-thread-");
        return executor;
    }
}
