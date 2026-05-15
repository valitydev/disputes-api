package dev.vality.disputes.provider.payments.service;

import dev.vality.disputes.config.properties.ProviderPaymentsCheckStatusSchedulerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProviderPaymentsCheckStatusScheduler implements InitializingBean, DisposableBean {

    private final ProviderPaymentsCheckStatusSchedulerProperties properties;
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public void afterPropertiesSet() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(properties.getPoolSize());
        taskScheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        taskScheduler.setWaitForTasksToCompleteOnShutdown(properties.getAwaitTermination());
        taskScheduler.setAwaitTerminationSeconds(properties.getAwaitTerminationPeriodSec());
        taskScheduler.initialize();
    }

    public void schedule(Runnable task, Instant startTime) {
        taskScheduler.schedule(task, startTime);
    }

    @Override
    public void destroy() {
        taskScheduler.shutdown();
    }
}
