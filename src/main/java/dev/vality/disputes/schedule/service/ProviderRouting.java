package dev.vality.disputes.schedule.service;

import dev.vality.disputes.exception.RoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderRouting {

    private static final String DISPUTES_URL_POSTFIX_DEFAULT = "disputes";
    private static final String OPTION_DISPUTES_URL_FIELD_NAME = "disputes_url";

    @Cacheable(value = "adapters", key = "defaultProviderUrl", cacheManager = "adaptersCacheManager")
    public String getRouteUrl(Map<String, String> options, String defaultProviderUrl) {
        var url = options.get(OPTION_DISPUTES_URL_FIELD_NAME);
        if (ObjectUtils.isEmpty(url)) {
            url = createDefaultRouteUrl(defaultProviderUrl);
        }
        return url;
    }

    private String createDefaultRouteUrl(String defaultProviderUrl) {
        log.debug("Creating url by appending postfix");
        try {
            var validUri = new URL(defaultProviderUrl).toURI();
            return UriComponentsBuilder.fromUri(validUri)
                    .pathSegment(DISPUTES_URL_POSTFIX_DEFAULT)
                    .encode()
                    .build()
                    .toUriString();
        } catch (Exception e) {
            throw new RoutingException("Unable to create default provider url: ", e);
        }
    }
}
