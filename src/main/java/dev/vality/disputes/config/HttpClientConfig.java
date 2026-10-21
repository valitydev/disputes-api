package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.HttpClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final HttpClientProperties httpClientProperties;

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
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager manager,
            RequestConfig requestConfig) {
        return HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .setConnectionManagerShared(true)
                .build();
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
