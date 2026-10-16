package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.ProviderPaymentsAdjustmentSchedulerProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String CREATED_DISPUTES_SCHEDULER = "createdDisputesScheduler";
    public static final String PENDING_DISPUTES_SCHEDULER = "pendingDisputesScheduler";
    public static final String FORGOTTEN_DISPUTES_SCHEDULER = "forgottenDisputesScheduler";
    public static final String NOTIFICATION_SCHEDULER = "notificationScheduler";
    public static final String PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER = "providerPaymentsAdjustmentScheduler";

    /**
     * Spring Boot backs off its own {@code taskScheduler} as soon as any other {@code TaskScheduler} bean exists,
     * so the fallback for unqualified {@code @Scheduled} tasks has to be declared explicitly.
     * It is still built from {@code spring.task.scheduling.*} through the auto-configured builder.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean(CREATED_DISPUTES_SCHEDULER)
    public ThreadPoolTaskScheduler createdDisputesScheduler() {
        return disputeScheduler("dispute-created-scheduler-");
    }

    @Bean(PENDING_DISPUTES_SCHEDULER)
    public ThreadPoolTaskScheduler pendingDisputesScheduler() {
        return disputeScheduler("dispute-pending-scheduler-");
    }

    @Bean(FORGOTTEN_DISPUTES_SCHEDULER)
    public ThreadPoolTaskScheduler forgottenDisputesScheduler() {
        return disputeScheduler("dispute-forgotten-scheduler-");
    }

    @Bean(NOTIFICATION_SCHEDULER)
    public ThreadPoolTaskScheduler notificationScheduler() {
        return disputeScheduler("dispute-notification-scheduler-");
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

    private static ThreadPoolTaskScheduler disputeScheduler(String threadNamePrefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
