package dev.vality.disputes.remote;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.time.Duration;

/**
 * Applies the {@link RemoteCallBudget} of background processing to every request that goes through the wrapped
 * client; requests made outside of background processing are passed through untouched.
 * <p>
 * Inside a budget the pool wait, connect and response timeouts of the request are clamped to the configured limits
 * and to the time that is left, but never raised above what the client already enforces ({@link Limits}). A
 * watchdog cancels the request when the budget is spent while it is in flight, and the failure is reported as an
 * {@link IOException} that Woody maps to a transient error. The wrapped client must not retry on its own: a retry
 * would run outside of the budget accounting.
 */
public class BudgetedHttpClient extends CloseableHttpClient {

    private final CloseableHttpClient delegate;
    private final RemoteCallBudget budget;
    private final Limits limits;
    private final boolean closeDelegate;

    /**
     * @param delegate      client that performs the requests
     * @param budget        shared budget of background processing
     * @param limits        timeouts that the delegate already enforces
     * @param closeDelegate whether closing this client closes the delegate; false when the delegate is shared
     */
    public BudgetedHttpClient(CloseableHttpClient delegate, RemoteCallBudget budget, Limits limits,
                              boolean closeDelegate) {
        this.delegate = delegate;
        this.budget = budget;
        this.limits = limits;
        this.closeDelegate = closeDelegate;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request, HttpContext context)
            throws IOException {
        var call = budget.beginCall().orElse(null);
        if (call == null) {
            return delegate.execute(target, request, context);
        }
        if (call.isExhausted()) {
            throw new ConnectException("Remote call budget is exhausted, the request was not sent");
        }
        var clientContext = HttpClientContext.castOrCreate(context);
        clamp(request, clientContext, call.remaining());
        var watch = call.watch(() -> cancel(request));
        CloseableHttpResponse response;
        try {
            response = delegate.execute(target, request, clientContext);
        } catch (IOException ex) {
            var fired = watch.fired();
            watch.disarm();
            throw translate(ex, fired);
        } catch (RuntimeException ex) {
            var fired = watch.fired();
            watch.disarm();
            if (fired && ex instanceof CancellationException) {
                // cancelling a request that still waits for a pooled connection surfaces unchecked
                throw translate(new IOException(ex), true);
            }
            throw ex;
        }
        var entity = response.getEntity();
        if (entity == null) {
            watch.disarm();
        } else {
            response.setEntity(new DisarmingEntity(entity, watch));
        }
        return response;
    }

    @Override
    public void close() throws IOException {
        if (closeDelegate) {
            delegate.close();
        }
    }

    @Override
    public void close(CloseMode closeMode) {
        if (closeDelegate) {
            delegate.close(closeMode);
        }
    }

    @SuppressWarnings("deprecation")
    private void clamp(ClassicHttpRequest request, HttpClientContext context, Duration remaining) {
        var base = request instanceof Configurable configurable ? configurable.getConfig() : null;
        if (base == null) {
            base = limits.defaults() != null ? limits.defaults() : context.getRequestConfig();
        }
        if (base == null) {
            base = RequestConfig.DEFAULT;
        }
        var clamped = RequestConfig.copy(base)
                .setConnectTimeout(tightest(remaining, budget.connectTimeout(), base.getConnectTimeout(),
                        limits.connect()))
                .setConnectionRequestTimeout(tightest(remaining, budget.poolTimeout(),
                        base.getConnectionRequestTimeout(), limits.pool()))
                .setResponseTimeout(tightest(remaining, null, base.getResponseTimeout(), limits.response()))
                .build();
        if (request instanceof HttpUriRequestBase uriRequest) {
            uriRequest.setConfig(clamped);
        }
        context.setRequestConfig(clamped);
    }

    private static Timeout tightest(Duration remaining, Duration configured, Timeout... existing) {
        var millis = Math.max(1, remaining.toMillis());
        if (configured != null) {
            millis = Math.min(millis, Math.max(1, configured.toMillis()));
        }
        for (var timeout : existing) {
            if (timeout != null && timeout.isEnabled()) {
                millis = Math.min(millis, Math.max(1, timeout.toMilliseconds()));
            }
        }
        return Timeout.ofMilliseconds(millis);
    }

    private static void cancel(ClassicHttpRequest request) {
        if (request instanceof Cancellable cancellable) {
            cancellable.cancel();
        }
    }

    /**
     * Woody maps transport failures by the exact class name of the exception or of one of its causes. The Apache
     * connect failures carry no cause and are not recognised, so the failures to reach the remote side are
     * reported as the plain JDK exceptions that Woody understands: {@link ConnectException} when nothing was sent
     * ("resource unavailable"), {@link SocketTimeoutException} when the request was in flight ("result unknown").
     */
    private static IOException translate(IOException ex, boolean deadlineFired) {
        if (deadlineFired) {
            var timeout = new SocketTimeoutException("Remote call exceeded its budget and was cancelled");
            timeout.initCause(ex);
            return timeout;
        }
        if (ex instanceof ConnectTimeoutException
                || ex instanceof HttpHostConnectException
                || ex instanceof ConnectionRequestTimeoutException) {
            var connectException = new ConnectException(ex.getMessage());
            connectException.initCause(ex);
            return connectException;
        }
        return ex;
    }

    /**
     * Timeouts that a client enforces by itself. They are never raised by the budget.
     *
     * @param defaults default request config of the client, applied to requests that carry none
     * @param connect  connect timeout configured on the client's connection manager
     * @param response response timeout configured on the client's connection manager
     * @param pool     pool wait timeout configured on the client
     */
    public record Limits(RequestConfig defaults, Timeout connect, Timeout response, Timeout pool) {

        public static Limits none() {
            return new Limits(null, null, null, null);
        }
    }

    /**
     * The response body is read after the request returns, so the watchdog has to stay armed until it is closed.
     */
    private static final class DisarmingEntity extends HttpEntityWrapper {

        private final RemoteCallBudget.Watch watch;

        private DisarmingEntity(HttpEntity entity, RemoteCallBudget.Watch watch) {
            super(entity);
            this.watch = watch;
        }

        @Override
        public InputStream getContent() throws IOException {
            return new FilterInputStream(super.getContent()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        watch.disarm();
                    }
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                watch.disarm();
            }
        }
    }
}
