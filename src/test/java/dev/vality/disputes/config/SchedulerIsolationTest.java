package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.ProviderPaymentsAdjustmentSchedulerProperties;
import dev.vality.disputes.retry.BackgroundProcessing;
import dev.vality.disputes.retry.ScheduledRetryService;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.schedule.ProviderPaymentsTask;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.schedule.CreatedDisputesTask;
import dev.vality.disputes.schedule.ForgottenDisputesTask;
import dev.vality.disputes.schedule.NotificationTask;
import dev.vality.disputes.schedule.PendingDisputesTask;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.core.ForgottenDisputesService;
import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Wires the real scheduled tasks on top of the real scheduler configuration, with mocked services and no database,
 * and checks on which threads the scheduled calls actually run.
 */
class SchedulerIsolationTest {

    private static final Duration AWAIT = Duration.ofSeconds(10);
    private static final String DEFAULT_SCHEDULER_PREFIX = "scheduling-";
    private static final String CREATED_SCHEDULER_PREFIX = "dispute-created-scheduler-";
    private static final String ADJUSTMENT_SCHEDULER_PREFIX = "provider-payments-adjustment-scheduler-";
    private static final String ADJUSTMENT_WORKER_MARKER = "provider-payments-exec-";

    @Test
    void adjustmentsRunWhileCreatedDisputeBlocksItsScheduler() throws Exception {
        var services = new Services();
        var blockedCreated = new BlockedCreatedDispute(services.created);
        var adjustment = new DueAdjustment(services.providerPayments);
        var schedulers = new ArrayList<ThreadPoolTaskScheduler>();

        try (var context = start(IsolatedSchedulersConfig.class, services,
                "dispute.isScheduleCreatedEnabled=true",
                "provider.payments.isScheduleCreateAdjustmentsEnabled=true")) {
            schedulers.addAll(context.getBeansOfType(ThreadPoolTaskScheduler.class).values());
            try {
                blockedCreated.awaitSchedulerWaitingForWorker();
                adjustment.makeDue();

                assertThat(adjustment.handled.await(AWAIT.toSeconds(), TimeUnit.SECONDS))
                        .as("adjustment is processed while the created dispute is still blocked").isTrue();

                assertThat(blockedCreated.release.getCount()).as("created worker is still blocked").isEqualTo(1);
                assertThat(blockedCreated.schedulerThread.get().getState())
                        .as("created scheduler thread is still waiting for its batch")
                        .isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);
                assertThat(blockedCreated.schedulerThread.get().getName()).startsWith(CREATED_SCHEDULER_PREFIX);
                assertThat(adjustment.polledOn.get()).startsWith(ADJUSTMENT_SCHEDULER_PREFIX);
                assertThat(adjustment.handledOn.get())
                        .contains(ADJUSTMENT_WORKER_MARKER)
                        .doesNotStartWith(ADJUSTMENT_SCHEDULER_PREFIX);
            } finally {
                blockedCreated.release.countDown();
            }
        }

        await().atMost(AWAIT).untilAsserted(() -> assertThat(schedulers).allSatisfy(scheduler ->
                assertThat(scheduler.getScheduledThreadPoolExecutor().isTerminated())
                        .as("scheduler is terminated after context close").isTrue()));
    }

    @Test
    void disabledAdjustmentsKeepOtherTasksOnTheirOwnSchedulers() {
        var services = new Services();
        var polledOn = new ConcurrentHashMap<String, String>();
        when(services.created.getCreatedSkipLocked(anyInt())).thenAnswer(inv -> record(polledOn, "created"));
        when(services.pending.getPendingSkipLocked(anyInt())).thenAnswer(inv -> record(polledOn, "pending"));
        when(services.forgotten.getForgottenSkipLocked(anyInt())).thenAnswer(inv -> record(polledOn, "forgotten"));
        when(services.notification.getNotifyRequests(anyInt())).thenAnswer(inv -> record(polledOn, "notification"));

        try (var context = start(IsolatedSchedulersConfig.class, services,
                "dispute.isScheduleCreatedEnabled=true",
                "dispute.isSchedulePendingEnabled=true",
                "dispute.isScheduleForgottenEnabled=true",
                "dispute.isScheduleNotificationEnabled=true",
                "provider.payments.isScheduleCreateAdjustmentsEnabled=false")) {
            await().atMost(AWAIT).until(() -> polledOn.size() == 4);

            assertThat(context.getBeansOfType(ProviderPaymentsTask.class)).isEmpty();
            verifyNoInteractions(services.providerPayments);
            assertThat(polledOn).containsOnlyKeys("created", "pending", "forgotten", "notification");
            Map.of(
                    "created", CREATED_SCHEDULER_PREFIX,
                    "pending", "dispute-pending-scheduler-",
                    "forgotten", "dispute-forgotten-scheduler-",
                    "notification", "dispute-notification-scheduler-")
                    .forEach((task, prefix) -> assertThat(polledOn.get(task)).startsWith(prefix));

            assertThat(context.getBeansOfType(TaskScheduler.class)).containsOnlyKeys(
                    "taskScheduler",
                    SchedulingConfig.CREATED_DISPUTES_SCHEDULER,
                    SchedulingConfig.PENDING_DISPUTES_SCHEDULER,
                    SchedulingConfig.FORGOTTEN_DISPUTES_SCHEDULER,
                    SchedulingConfig.NOTIFICATION_SCHEDULER,
                    SchedulingConfig.PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER);
            var defaultScheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
            var adjustmentScheduler = context.getBean(
                    SchedulingConfig.PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER, ThreadPoolTaskScheduler.class);
            assertThat(defaultScheduler).isNotSameAs(adjustmentScheduler);
            assertThat(defaultScheduler.getThreadNamePrefix()).isEqualTo(DEFAULT_SCHEDULER_PREFIX);
            assertThat(adjustmentScheduler.getThreadNamePrefix()).isEqualTo(ADJUSTMENT_SCHEDULER_PREFIX);
        }
    }

    private static List<?> record(Map<String, String> polledOn, String task) {
        polledOn.putIfAbsent(task, Thread.currentThread().getName());
        return List.of();
    }

    private static AnnotationConfigApplicationContext start(Class<?> config, Services services, String... flags) {
        var properties = new ArrayList<>(List.of(
                "dispute.batchSize=1",
                "dispute.fixedDelayCreated=50",
                "dispute.fixedDelayPending=50",
                "dispute.fixedDelayForgotten=50",
                "dispute.fixedDelayNotification=50",
                "dispute.initialDelayCreated=0",
                "dispute.initialDelayPending=0",
                "dispute.initialDelayForgotten=0",
                "dispute.initialDelayNotification=0",
                "provider.payments.batchSize=1",
                "provider.payments.fixedDelayCreateAdjustments=50",
                "provider.payments.initialDelayCreateAdjustments=0"));
        properties.addAll(List.of(flags));
        var context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(properties.toArray(String[]::new)).applyTo(context);
        context.register(config);
        context.registerBean(CreatedDisputesService.class, () -> services.created);
        context.registerBean(PendingDisputesService.class, () -> services.pending);
        context.registerBean(ForgottenDisputesService.class, () -> services.forgotten);
        context.registerBean(NotificationService.class, () -> services.notification);
        context.registerBean(ProviderPaymentsService.class, () -> services.providerPayments);
        context.refresh();
        return context;
    }

    private static class Services {

        final CreatedDisputesService created = mock(CreatedDisputesService.class);
        final PendingDisputesService pending = mock(PendingDisputesService.class);
        final ForgottenDisputesService forgotten = mock(ForgottenDisputesService.class);
        final NotificationService notification = mock(NotificationService.class);
        final ProviderPaymentsService providerPayments = mock(ProviderPaymentsService.class);
    }

    /**
     * One created dispute whose remote call blocks until {@link #release} is counted down. The scheduler thread that
     * fetched it is then parked in {@code invokeAll}, exactly like during a slow RPC.
     */
    private static class BlockedCreatedDispute {

        final AtomicReference<Thread> schedulerThread = new AtomicReference<>();
        final CountDownLatch workerEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockedCreatedDispute(CreatedDisputesService created) {
            var polls = new AtomicInteger();
            when(created.getCreatedSkipLocked(anyInt())).thenAnswer(inv -> {
                if (polls.getAndIncrement() > 0) {
                    return List.of();
                }
                schedulerThread.set(Thread.currentThread());
                var dispute = new Dispute();
                dispute.setId(UUID.randomUUID());
                return List.of(dispute);
            });
            doAnswer(inv -> {
                workerEntered.countDown();
                release.await(AWAIT.toSeconds() * 3, TimeUnit.SECONDS);
                return null;
            }).when(created).callCreateDisputeRemotely(any());
        }

        void awaitSchedulerWaitingForWorker() throws InterruptedException {
            assertThat(workerEntered.await(AWAIT.toSeconds(), TimeUnit.SECONDS))
                    .as("created worker entered the slow remote call").isTrue();
            await().atMost(AWAIT).untilAsserted(() -> {
                var thread = schedulerThread.get();
                assertThat(thread.getState()).isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);
                assertThat(thread.getStackTrace()).extracting(StackTraceElement::getMethodName)
                        .contains("invokeAll");
            });
        }
    }

    /**
     * One callback that {@code getPaymentsForHgCall} hands out only after {@link #makeDue()}, so that an adjustment
     * processed before the created dispute blocked cannot count as evidence.
     */
    private static class DueAdjustment {

        final AtomicBoolean due = new AtomicBoolean();
        final AtomicReference<String> polledOn = new AtomicReference<>();
        final AtomicReference<String> handledOn = new AtomicReference<>();
        final CountDownLatch handled = new CountDownLatch(1);

        DueAdjustment(ProviderPaymentsService providerPayments) {
            when(providerPayments.getPaymentsForHgCall(anyInt())).thenAnswer(inv -> {
                if (!due.compareAndSet(true, false)) {
                    return List.of();
                }
                polledOn.set(Thread.currentThread().getName());
                var callback = new ProviderCallback();
                callback.setId(UUID.randomUUID());
                return List.of(callback);
            });
            doAnswer(inv -> {
                handledOn.set(Thread.currentThread().getName());
                handled.countDown();
                return null;
            }).when(providerPayments).callHgForCreateAdjustment(any());
        }

        void makeDue() {
            due.set(true);
        }
    }

    @Configuration
    @EnableScheduling
    @ImportAutoConfiguration(TaskSchedulingAutoConfiguration.class)
    @Import({
            ApplicationConfig.class,
            ProviderPaymentsAdjustmentSchedulerProperties.class,
            CreatedDisputesTask.class,
            PendingDisputesTask.class,
            ForgottenDisputesTask.class,
            NotificationTask.class,
            ProviderPaymentsTask.class})
    static class TasksConfig {
        @Bean
        public BackgroundProcessing backgroundProcessing() {
            return new BackgroundProcessing(mock(ScheduledRetryService.class));
        }
    }

    @Configuration
    @Import({TasksConfig.class, SchedulingConfig.class})
    static class IsolatedSchedulersConfig {
    }

}
