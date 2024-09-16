package dev.vality.disputes.schedule.service;

import dev.vality.disputes.exception.RoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderRouting {

    private static final String DISPUTES_URL_POSTFIX_DEFAULT = "disputes";
    private static final String OPTION_DISPUTES_URL_FIELD_NAME = "disputes_url";

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
            return UriComponentsBuilder.fromUri(URI.create(defaultProviderUrl))
                    .pathSegment(DISPUTES_URL_POSTFIX_DEFAULT)
                    .encode()
                    .build()
                    .toUriString();
        } catch (Exception e) {
            throw new RoutingException("Unable to create default provider url: ", e);
        }
    }
}
