package dev.vality.disputes.config;

import dev.vality.disputes.config.properties.HttpClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final HttpClientProperties httpClientProperties;

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

    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        var connectionManager = new PoolingHttpClientConnectionManager(connectionSocketFactory());
        connectionManager.setMaxTotal(httpClientProperties.getMaxTotalPooling());
        connectionManager.setDefaultMaxPerRoute(httpClientProperties.getDefaultMaxPerRoute());
        connectionManager.setDefaultConnectionConfig(connectionConfig(httpClientProperties));
        return connectionManager;
    }

    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpClientProperties.getPoolTimeout()))
                .build();
    }

    @SneakyThrows
    private Registry<ConnectionSocketFactory> connectionSocketFactory() {
        var sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustAllStrategy()).build();
        var sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslConnectionSocketFactory)
                .register("http", new PlainConnectionSocketFactory())
                .build();
    }

    private ConnectionConfig connectionConfig(HttpClientProperties httpClientProperties) {
        return ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(httpClientProperties.getConnectionTimeout()))
                .setSocketTimeout(Timeout.ofMilliseconds(httpClientProperties.getRequestTimeout()))
                .build();
    }
}
