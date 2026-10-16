package dev.vality.disputes.remote;

import dev.vality.disputes.config.properties.RemoteCallProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Time budget of one logical external operation made by background processing.
 * <p>
 * The budget is thread-bound and only active between {@link #runInBackground} calls: the same Hellgate and adapter
 * clients also serve interactive API requests, and those calls must keep their current behaviour. Inside an active
 * scope every call that goes through a {@link BudgetedHttpClient} gets its own budget of
 * {@code background-processing.remote-call.max-duration}, unless a surrounding {@link #asOneOperation} scope already
 * fixed the deadline for a composite operation (for example createDispute, the HTTP availability check and the
 * fallback createDispute).
 * <p>
 * Pool wait, connect and response timeouts are clamped to the time that is left. A watchdog cancels the request at
 * the deadline as a backstop for I/O that is not covered by a socket timeout (TLS handshake, a response that is
 * trickled byte by byte).
 */
@Slf4j
@Component
public class RemoteCallBudget {

    private final RemoteCallProperties properties;
    private final LongSupplier nanoTime;
    private final ThreadLocal<Scope> scopes = new ThreadLocal<>();
    private final ScheduledExecutorService watchdog;

    @Autowired
    public RemoteCallBudget(RemoteCallProperties properties) {
        this(properties, System::nanoTime);
    }

    RemoteCallBudget(RemoteCallProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
        var executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            var thread = new Thread(runnable, "remote-call-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        this.watchdog = executor;
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }

    /**
     * @return the budget of one logical remote operation
     */
    public Duration maxDuration() {
        return properties.getMaxDuration();
    }

    public void runInBackground(Runnable operation) {
        runInBackground(() -> {
            operation.run();
            return null;
        });
    }

    public <T> T runInBackground(Supplier<T> operation) {
        var previous = scopes.get();
        scopes.set(previous != null ? previous : new Scope());
        try {
            return operation.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Makes everything executed by {@code operation} share one deadline. Outside of a background scope, and inside
     * an already running composite operation, it changes nothing.
     */
    public void asOneOperation(Runnable operation) {
        var scope = scopes.get();
        if (scope == null || scope.deadlineNanos != null) {
            operation.run();
            return;
        }
        scope.deadlineNanos = nanoTime.getAsLong() + properties.getMaxDuration().toNanos();
        try {
            operation.run();
        } finally {
            scope.deadlineNanos = null;
        }
    }

    public boolean isBackground() {
        return scopes.get() != null;
    }

    public Duration connectTimeout() {
        return properties.getConnectTimeout();
    }

    public Duration poolTimeout() {
        return properties.getPoolTimeout();
    }

    /**
     * Starts accounting for one external call. Empty when the calling thread is not running background processing.
     */
    Optional<Call> beginCall() {
        var scope = scopes.get();
        if (scope == null) {
            return Optional.empty();
        }
        var deadline = scope.deadlineNanos != null
                ? scope.deadlineNanos
                : nanoTime.getAsLong() + properties.getMaxDuration().toNanos();
        return Optional.of(new Call(deadline));
    }

    private void restore(Scope previous) {
        if (previous == null) {
            scopes.remove();
        } else {
            scopes.set(previous);
        }
    }

    private static final class Scope {
        private Long deadlineNanos;
    }

    final class Call {

        private final long deadlineNanos;

        private Call(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        Duration remaining() {
            return Duration.ofNanos(Math.max(0, deadlineNanos - nanoTime.getAsLong()));
        }

        boolean isExhausted() {
            return deadlineNanos - nanoTime.getAsLong() <= 0;
        }

        /**
         * Runs {@code onDeadline} once the budget is spent unless the returned watch is disarmed before.
         */
        Watch watch(Runnable onDeadline) {
            var watch = new Watch();
            var delayNanos = Math.max(0, deadlineNanos - nanoTime.getAsLong());
            try {
                watch.future = watchdog.schedule(() -> {
                    if (watch.fired.compareAndSet(false, true)) {
                        try {
                            onDeadline.run();
                        } catch (RuntimeException ex) {
                            log.warn("Failed to cancel remote call that exceeded its budget", ex);
                        }
                    }
                }, delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException ex) {
                // the application is shutting down: the call goes on bounded by the clamped timeouts only
                log.debug("Remote call watchdog is stopped, the call is not watched");
            }
            return watch;
        }
    }

    static final class Watch {

        private final AtomicBoolean fired = new AtomicBoolean();
        private volatile ScheduledFuture<?> future;

        boolean fired() {
            return fired.get();
        }

        void disarm() {
            var scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }
}
