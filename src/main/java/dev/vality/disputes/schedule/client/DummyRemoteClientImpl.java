package dev.vality.disputes.schedule.client;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeCreatedSuccessResult;
import dev.vality.disputes.schedule.model.ProviderData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.provider.enabled", havingValue = "false")
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class DummyRemoteClientImpl implements DefaultRemoteClient {

    @Override
    public Boolean routeUrlEquals(ProviderData providerData) {
        return false;
    }

    @Override
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData) {
        return DisputeCreatedResult.successResult(new DisputeCreatedSuccessResult(UUID.randomUUID().toString()));
    }
}
