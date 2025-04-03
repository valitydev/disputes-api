package dev.vality.disputes.schedule.client;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeCreatedSuccessResult;
import dev.vality.disputes.schedule.model.ProviderData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.provider.enabled", havingValue = "false")
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DummyRemoteClientImpl implements DefaultRemoteClient {

    private final String routeUrl = "tg-bot";

    @Override
    public Boolean routeUrlEquals(ProviderData providerData) {
        return StringUtils.equalsIgnoreCase(providerData.getRouteUrl(), routeUrl);
    }

    @Override
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData, TransactionInfo transactionInfo) {
        log.debug("Trying to call DummyRemoteClientImpl.createDispute() {}", dispute.getId());
        providerData.setRouteUrl(routeUrl);
        return DisputeCreatedResult.successResult(new DisputeCreatedSuccessResult(UUID.randomUUID().toString()));
    }
}
