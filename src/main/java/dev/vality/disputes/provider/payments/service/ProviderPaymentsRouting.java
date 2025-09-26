package dev.vality.disputes.provider.payments.service;

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
public class ProviderPaymentsRouting {

    private static final String PAYMENTS_URL_POSTFIX_DEFAULT = "provider-payments";
    private static final String OPTION_PROVIDER_PAYMENTS_URL_FIELD_NAME = "provider_payments_url";

    public void initRouteUrl(ProviderData providerData) {
        var url = providerData.getOptions().get(OPTION_PROVIDER_PAYMENTS_URL_FIELD_NAME);
        if (ObjectUtils.isEmpty(url)) {
            url = createDefaultRouteUrl(providerData.getDefaultProviderUrl());
        }
        providerData.setRouteUrl(url);
    }

    private String createDefaultRouteUrl(String defaultProviderUrl) {
        try {
            return UriComponentsBuilder.fromUri(URI.create(defaultProviderUrl))
                    .pathSegment(PAYMENTS_URL_POSTFIX_DEFAULT)
                    .encode()
                    .build()
                    .toUriString();
        } catch (Throwable ex) {
            throw new RoutingException("Unable to create default provider url: ", ex);
        }
    }
}
