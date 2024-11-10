package dev.vality.disputes.schedule.client;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.converter.DisputeParamsConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.provider.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputesTgBotRemoteClientImpl implements DefaultRemoteClient {

    private final DisputesTgBotService disputesTgBotService;
    private final DisputeParamsConverter disputeParamsConverter;

    @Value("${service.disputes-tg-bot.provider.url}")
    private String routeUrl;

    @Override
    public Boolean routeUrlEquals(ProviderData providerData) {
        return StringUtils.equalsIgnoreCase(providerData.getRouteUrl(), routeUrl);
    }

    @Override
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData) {
        log.debug("Trying to build disputeParams {}", dispute.getId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, providerData.getOptions());
        providerData.setRouteUrl(routeUrl);
        log.debug("Trying to disputesTgBotService.createDispute() call {}", dispute.getId());
        var result = disputesTgBotService.createDispute(disputeParams);
        log.info("disputesTgBotService.createDispute() has been called {} {}", dispute.getId(), result);
        return result;
    }
}
