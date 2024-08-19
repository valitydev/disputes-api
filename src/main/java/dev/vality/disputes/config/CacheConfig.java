package dev.vality.disputes.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.disputes.config.properties.DominantCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final AdaptersConnectionProperties adaptersConnectionProperties;
    private final DominantCacheProperties dominantCacheProperties;

    @Bean
    @Primary
    public CacheManager adaptersCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(adaptersConnectionsCacheConfig());
        caffeineCacheManager.setCacheNames(List.of("adapters"));
        return caffeineCacheManager;
    }

    private Caffeine adaptersConnectionsCacheConfig() {
        return Caffeine.newBuilder()
                .expireAfterAccess(adaptersConnectionProperties.getTtlMin(), TimeUnit.MINUTES)
                .maximumSize(adaptersConnectionProperties.getPoolSize());
    }

    @Bean
    public CacheManager currenciesCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getCurrencies()));
        caffeineCacheManager.setCacheNames(List.of("currencies"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager terminalsCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getTerminals()));
        caffeineCacheManager.setCacheNames(List.of("terminals"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager providersCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getProviders()));
        caffeineCacheManager.setCacheNames(List.of("providers"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager paymentServicesCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getPaymentServices()));
        caffeineCacheManager.setCacheNames(List.of("payment_services"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager proxiesCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getProxies()));
        caffeineCacheManager.setCacheNames(List.of("proxies"));
        return caffeineCacheManager;
    }

    private Caffeine getCacheConfig(DominantCacheProperties.CacheConfig cacheConfig) {
        return Caffeine.newBuilder()
                .expireAfterAccess(cacheConfig.getTtlSec(), TimeUnit.SECONDS)
                .maximumSize(cacheConfig.getPoolSize());
    }

}
