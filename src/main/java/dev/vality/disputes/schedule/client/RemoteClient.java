package dev.vality.disputes.schedule.client;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.converter.DisputeContextConverter;
import dev.vality.disputes.schedule.converter.DisputeParamsConverter;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.service.ProviderDisputesRouting;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteClient {

    private final ProviderDisputesRouting providerDisputesRouting;
    private final ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    private final DisputeParamsConverter disputeParamsConverter;
    private final DisputeContextConverter disputeContextConverter;

    @SneakyThrows
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData,
                                              TransactionInfo transactionInfo) {
        providerDisputesRouting.initRouteUrl(providerData);
        log.info("Trying to call ProviderDisputesThriftInterfaceBuilder.createDispute() {}", dispute.getId());
        var remoteClient = providerDisputesThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
        log.debug("Trying to build disputeParams {}", dispute.getId());
        var disputeParams =
                disputeParamsConverter.convert(dispute, attachments, providerData.getOptions(), transactionInfo);
        log.debug("Trying to routed remote provider's createDispute() call {}", dispute.getId());
        var result = remoteClient.createDispute(disputeParams);
        log.debug("Routed remote provider's createDispute() has been called {} {}", dispute.getId(), result);
        return result;
    }

    @SneakyThrows
    public DisputeStatusResult checkDisputeStatus(Dispute dispute, ProviderDispute providerDispute,
                                                  ProviderData providerData, TransactionInfo transactionInfo) {
        providerDisputesRouting.initRouteUrl(providerData);
        log.info("Trying to call ProviderDisputesThriftInterfaceBuilder.checkDisputeStatus() {}", dispute.getId());
        var remoteClient = providerDisputesThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
        log.debug("Trying to build disputeContext {}", dispute.getId());
        var disputeContext =
                disputeContextConverter.convert(dispute, providerDispute, providerData.getOptions(), transactionInfo);
        log.debug("Trying to routed remote provider's checkDisputeStatus() call {}", dispute.getId());
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.debug("Routed remote provider's checkDisputeStatus() has been called {} {}", dispute.getId(), result);
        return result;
    }
}
