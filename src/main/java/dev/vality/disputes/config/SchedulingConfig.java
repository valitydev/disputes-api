package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.ProviderPaymentsAdjustmentSchedulerProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER = "providerPaymentsAdjustmentScheduler";

    /**
     * Spring Boot backs off its own {@code taskScheduler} as soon as any other {@code TaskScheduler} bean exists,
     * so the default scheduler for the remaining {@code @Scheduled} tasks has to be declared explicitly.
     * It is still built from {@code spring.task.scheduling.*} through the auto-configured builder.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean(PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER)
    public ThreadPoolTaskScheduler providerPaymentsAdjustmentScheduler(
            ProviderPaymentsAdjustmentSchedulerProperties properties) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(properties.getAwaitTermination());
        scheduler.setAwaitTerminationSeconds(properties.getAwaitTerminationPeriodSec());
        return scheduler;
    }
}
