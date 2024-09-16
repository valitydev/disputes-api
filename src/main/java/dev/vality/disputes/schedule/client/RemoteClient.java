package dev.vality.disputes.schedule.client;

import dev.vality.damsel.domain.ProviderRef;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.disputes.Attachment;
import dev.vality.disputes.DisputeCreatedResult;
import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
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
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, terminal.getOptions());
        var remoteClient = providerIfaceBuilder.build(terminal.getOptions(), proxy.getUrl());
        log.info("Trying to routed remote provider's createDispute() call {}", dispute);
        var result = remoteClient.createDispute(disputeParams);
        log.debug("Routed remote provider's createDispute() has been called {}", dispute);
        return result;
    }

    @SneakyThrows
    public DisputeStatusResult checkDisputeStatus(Dispute dispute, ProviderDispute providerDispute) {
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeContext = disputeContextConverter.convert(dispute, providerDispute, terminal.getOptions());
        var remoteClient = providerIfaceBuilder.build(terminal.getOptions(), proxy.getUrl());
        log.info("Trying to routed remote provider's checkDisputeStatus() call {}", dispute);
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.debug("Routed remote provider's checkDisputeStatus() has been called {}", dispute);
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
