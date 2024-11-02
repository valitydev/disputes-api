package dev.vality.disputes.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.disputes.config.properties.DominantCacheProperties;
import dev.vality.disputes.config.properties.PartyManagementCacheProperties;
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
    private final PartyManagementCacheProperties partyManagementCacheProperties;

    @Bean
    @Primary
    public CacheManager providersDisputesCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(adaptersConnectionsCacheConfig());
        caffeineCacheManager.setCacheNames(List.of("providersDisputes"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager providersPaymentsCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(adaptersConnectionsCacheConfig());
        caffeineCacheManager.setCacheNames(List.of("providersPayments"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager currenciesCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getCurrencies()));
        caffeineCacheManager.setCacheNames(List.of("currencies"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager terminalsCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getTerminals()));
        caffeineCacheManager.setCacheNames(List.of("terminals"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager providersCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getProviders()));
        caffeineCacheManager.setCacheNames(List.of("providers"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager proxiesCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(dominantCacheProperties.getProxies()));
        caffeineCacheManager.setCacheNames(List.of("proxies"));
        return caffeineCacheManager;
    }

    @Bean
    public CacheManager shopsCacheManager() {
        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(getCacheConfig(partyManagementCacheProperties.getShops()));
        caffeineCacheManager.setCacheNames(List.of("shops"));
        return caffeineCacheManager;
    }

    private Caffeine adaptersConnectionsCacheConfig() {
        return Caffeine.newBuilder()
                .expireAfterAccess(adaptersConnectionProperties.getTtlMin(), TimeUnit.MINUTES)
                .maximumSize(adaptersConnectionProperties.getPoolSize());
    }

    private Caffeine getCacheConfig(DominantCacheProperties.CacheConfig cacheConfig) {
        return Caffeine.newBuilder()
                .expireAfterAccess(cacheConfig.getTtlSec(), TimeUnit.SECONDS)
                .maximumSize(cacheConfig.getPoolSize());
    }

    private Caffeine getCacheConfig(PartyManagementCacheProperties.CacheConfig cacheConfig) {
        return Caffeine.newBuilder()
                .expireAfterAccess(cacheConfig.getTtlSec(), TimeUnit.SECONDS)
                .maximumSize(cacheConfig.getPoolSize());
    }
}
