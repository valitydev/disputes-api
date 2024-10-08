package dev.vality.disputes.schedule.client;

import dev.vality.damsel.domain.ProviderRef;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.converter.DisputeContextConverter;
import dev.vality.disputes.schedule.converter.DisputeParamsConverter;
import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import dev.vality.disputes.service.external.DominantService;
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

    private final ProviderIfaceBuilder providerIfaceBuilder;
    private final DominantService dominantService;
    private final DisputeContextConverter disputeContextConverter;
    private final DisputeParamsConverter disputeParamsConverter;

    @SneakyThrows
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments) {
        log.debug("Trying to call dominant for RemoteClient {}", dispute.getId());
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        log.debug("Trying to build disputeParams {}", dispute.getId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, terminal.getOptions());
        log.debug("Trying to call ProviderIfaceBuilder {}", dispute.getId());
        var remoteClient = providerIfaceBuilder.buildTHSpawnClient(terminal.getOptions(), proxy.getUrl());
        log.debug("Trying to routed remote provider's createDispute() call {}", dispute.getId());
        var result = remoteClient.createDispute(disputeParams);
        log.info("Routed remote provider's createDispute() has been called {} {}", dispute.getId(), result);
        return result;
    }

    @SneakyThrows
    public DisputeStatusResult checkDisputeStatus(Dispute dispute, ProviderDispute providerDispute) {
        log.debug("Trying to call dominant for RemoteClient {}", dispute.getId());
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        log.debug("Trying to build disputeContext {}", dispute.getId());
        var disputeContext = disputeContextConverter.convert(dispute, providerDispute, terminal.getOptions());
        log.debug("Trying to call ProviderIfaceBuilder {}", dispute.getId());
        var remoteClient = providerIfaceBuilder.buildTHSpawnClient(terminal.getOptions(), proxy.getUrl());
        log.debug("Trying to routed remote provider's checkDisputeStatus() call {}", dispute.getId());
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.info("Routed remote provider's checkDisputeStatus() has been called {} {}", dispute.getId(), result);
        return result;
    }

    private ProxyDefinition getProxy(Integer providerId) {
        var provider = dominantService.getProvider(new ProviderRef(providerId));
        return dominantService.getProxy(provider.getProxy().getRef());
    }

    private Terminal getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }
}
