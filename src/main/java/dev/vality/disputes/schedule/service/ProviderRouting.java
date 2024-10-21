package dev.vality.disputes.schedule.service;

import dev.vality.disputes.exception.RoutingException;
import dev.vality.disputes.schedule.model.ProviderData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderRouting {

    private static final String DISPUTES_URL_POSTFIX_DEFAULT = "disputes";
    private static final String OPTION_DISPUTES_URL_FIELD_NAME = "disputes_url";

    public String getRouteUrl(ProviderData providerData) {
        var url = providerData.getOptions().get(OPTION_DISPUTES_URL_FIELD_NAME);
        if (ObjectUtils.isEmpty(url)) {
            url = createDefaultRouteUrl(providerData.getDefaultProviderUrl());
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
