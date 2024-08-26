package dev.vality.disputes.polling;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.adapter.flow.lib.service.ExponentialBackOffPollingService;
import dev.vality.damsel.domain.Terminal;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DominantService;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ExponentialBackOffPollingServiceWrapper {

    private final ExponentialBackOffPollingService exponentialBackOffPollingService;
    private final DominantService dominantService;

    public ExponentialBackOffPollingServiceWrapper(DominantService dominantService) {
        this.dominantService = dominantService;
        this.exponentialBackOffPollingService = new ExponentialBackOffPollingService();
    }

    public LocalDateTime prepareNextPollingInterval(PollingInfo pollingInfo, Map<String, String> options) {
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, options);
        return getLocalDateTime(pollingInfo.getStartDateTimePolling().plusSeconds(seconds));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @SneakyThrows
    public LocalDateTime prepareNextPollingInterval(Dispute dispute) {
        var pollingInfo = new PollingInfo();
        var startDateTimePolling = dispute.getCreatedAt().toInstant(ZoneOffset.UTC);
        pollingInfo.setStartDateTimePolling(startDateTimePolling);
        pollingInfo.setMaxDateTimePolling(dispute.getPollingBefore().toInstant(ZoneOffset.UTC));
        var terminal = getTerminal(dispute.getTerminalId());
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(
                pollingInfo, terminal.get().getOptions());
        return getLocalDateTime(startDateTimePolling.plusSeconds(seconds));
    }

    private CompletableFuture<Terminal> getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
