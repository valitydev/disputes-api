package dev.vality.disputes.schedule.client;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.converter.DisputeContextConverter;
import dev.vality.disputes.schedule.converter.DisputeParamsConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import dev.vality.disputes.schedule.service.ProviderRouting;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class RemoteClient {

    private final ProviderRouting providerRouting;
    private final ProviderIfaceBuilder providerIfaceBuilder;
    private final DisputeParamsConverter disputeParamsConverter;
    private final DisputeContextConverter disputeContextConverter;

    @SneakyThrows
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData) {
        providerRouting.initRouteUrl(providerData);
        log.debug("Trying to call ProviderIfaceBuilder {}", dispute.getId());
        var remoteClient = providerIfaceBuilder.buildTHSpawnClient(providerData.getRouteUrl());
        log.debug("Trying to build disputeParams {}", dispute.getId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, providerData.getOptions());
        log.debug("Trying to routed remote provider's createDispute() call {}", dispute.getId());
        var result = remoteClient.createDispute(disputeParams);
        log.info("Routed remote provider's createDispute() has been called {} {}", dispute.getId(), result);
        return result;
    }

    @SneakyThrows
    public DisputeStatusResult checkDisputeStatus(Dispute dispute, ProviderDispute providerDispute, ProviderData providerData) {
        providerRouting.initRouteUrl(providerData);
        log.debug("Trying to call ProviderIfaceBuilder {}", dispute.getId());
        var remoteClient = providerIfaceBuilder.buildTHSpawnClient(providerData.getRouteUrl());
        log.debug("Trying to build disputeContext {}", dispute.getId());
        var disputeContext = disputeContextConverter.convert(dispute, providerDispute, providerData.getOptions());
        log.debug("Trying to routed remote provider's checkDisputeStatus() call {}", dispute.getId());
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.info("Routed remote provider's checkDisputeStatus() has been called {} {}", dispute.getId(), result);
        return result;
    }
}
