package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.HttpClientProperties;
import dev.vality.disputes.config.properties.BackgroundProcessingProperties;
import dev.vality.disputes.retry.BackgroundProcessing;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.ConnectException;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final HttpClientProperties httpClientProperties;
    private final BackgroundProcessingProperties backgroundProcessingProperties;

    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(defaultClientTlsStrategy())
                .setMaxConnTotal(httpClientProperties.getMaxTotalPooling())
                .setMaxConnPerRoute(httpClientProperties.getDefaultMaxPerRoute())
                .setDefaultConnectionConfig(connectionConfig()).build();
    }

    private ConnectionConfig connectionConfig() {
        return ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(httpClientProperties.getConnectionTimeout()))
                .setSocketTimeout(Timeout.ofMilliseconds(httpClientProperties.getRequestTimeout()))
                .build();
    }

    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpClientProperties.getPoolTimeout()))
                .build();
    }

    @Bean
    @Primary
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager manager,
            RequestConfig requestConfig) {
        return HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .addExecInterceptorFirst("background-timeouts", backgroundTimeouts())
                .setConnectionManagerShared(true)
                .build();
    }

    @Bean
    public CloseableHttpClient woodyHttpClient() {
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(httpClientProperties.getMaxTotalPooling())
                .setMaxConnPerRoute(httpClientProperties.getDefaultMaxPerRoute())
                .build();
        return HttpClients.custom()
                .setConnectionManager(manager)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableContentCompression()
                .addExecInterceptorFirst("background-timeouts", backgroundTimeouts())
                .build();
    }

    @SuppressWarnings("deprecation")
    private ExecChainHandler backgroundTimeouts() {
        return (request, scope, chain) -> {
            if (!BackgroundProcessing.isActive()) {
                return chain.proceed(request, scope);
            }
            var limits = backgroundProcessingProperties.getRemoteCall();
            var current = scope.clientContext.getRequestConfigOrDefault();
            scope.clientContext.setRequestConfig(RequestConfig.copy(current)
                    .setConnectTimeout(tightest(limits.getConnectTimeout(), current.getConnectTimeout()))
                    .setConnectionRequestTimeout(tightest(limits.getPoolTimeout(),
                            current.getConnectionRequestTimeout()))
                    .setResponseTimeout(tightest(limits.getResponseTimeout(), current.getResponseTimeout()))
                    .build());
            try {
                return chain.proceed(request, scope);
            } catch (ConnectTimeoutException | HttpHostConnectException | ConnectionRequestTimeoutException ex) {
                // Woody 3 maps exact JDK exception names, not these Apache subclasses.
                var unavailable = new ConnectException("Background RPC " + ex.getClass().getSimpleName());
                unavailable.initCause(ex);
                throw unavailable;
            }
        };
    }

    private static Timeout tightest(Duration limit, Timeout existing) {
        var millis = Math.max(1, limit.toMillis());
        if (existing != null && existing.isEnabled()) {
            millis = Math.min(millis, existing.toMilliseconds());
        }
        return Timeout.ofMilliseconds(millis);
    }

    @SneakyThrows
    private DefaultClientTlsStrategy defaultClientTlsStrategy() {
        var sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustAllStrategy()).build();
        return new DefaultClientTlsStrategy(
                sslContext,
                HostnameVerificationPolicy.CLIENT,
                NoopHostnameVerifier.INSTANCE);
    }
}
