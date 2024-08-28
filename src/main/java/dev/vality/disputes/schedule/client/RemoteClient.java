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
import dev.vality.disputes.schedule.service.ProviderRouting;
import dev.vality.disputes.service.external.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class RemoteClient {

    private final ProviderRouting providerRouting;
    private final DominantService dominantService;
    private final DisputeContextConverter disputeContextConverter;
    private final DisputeParamsConverter disputeParamsConverter;

    @Transactional(propagation = Propagation.REQUIRED)
    @SneakyThrows
    public DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments) {
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeParams = disputeParamsConverter.convert(dispute, attachments, terminal.get().getOptions());
        var remoteClient = providerRouting.getConnection(terminal.get().getOptions(), proxy.get().getUrl());
        log.info("Trying to routed remote provider's createDispute() call {}", dispute);
        var result = remoteClient.createDispute(disputeParams);
        log.debug("Routed remote provider's createDispute() has been called {}", dispute);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @SneakyThrows
    public DisputeStatusResult checkDisputeStatus(Dispute dispute, ProviderDispute providerDispute) {
        var terminal = getTerminal(dispute.getTerminalId());
        var proxy = getProxy(dispute.getProviderId());
        var disputeContext = disputeContextConverter.convert(dispute, providerDispute, terminal.get().getOptions());
        var remoteClient = providerRouting.getConnection(terminal.get().getOptions(), proxy.get().getUrl());
        log.info("Trying to routed remote provider's checkDisputeStatus() call {}", dispute);
        var result = remoteClient.checkDisputeStatus(disputeContext);
        log.debug("Routed remote provider's checkDisputeStatus() has been called {}", dispute);
        return result;
    }

    private CompletableFuture<ProxyDefinition> getProxy(Integer providerId) {
        return dominantService.getProxy(new ProviderRef(providerId));
    }

    private CompletableFuture<Terminal> getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }
}
