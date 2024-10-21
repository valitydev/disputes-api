package dev.vality.disputes.utils;

import dev.vality.damsel.domain.Provider;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_MAX_TIME_POLLING_MIN;

@UtilityClass
public class OptionsExtractors {

    public static Integer extractMaxTimePolling(Map<String, String> options, int maxTimePolling) {
        return Integer.parseInt(
                options.getOrDefault(DISPUTE_FLOW_MAX_TIME_POLLING_MIN, String.valueOf(maxTimePolling)));
    }

    @SneakyThrows
    public static Map<String, String> mergeOptions(
            CompletableFuture<Provider> provider,
            CompletableFuture<ProxyDefinition> proxy,
            CompletableFuture<Terminal> terminal) {
        var merged = new HashMap<String, String>();
        merged.putAll(safetyPut(provider.get().getProxy().getAdditional()));
        merged.putAll(safetyPut(proxy.get().getOptions()));
        merged.putAll(safetyPut(terminal.get().getOptions()));
        return merged;
    }

    public static Map<String, String> mergeOptions(Provider provider, ProxyDefinition proxy, Terminal terminal) {
        var merged = new HashMap<String, String>();
        merged.putAll(safetyPut(provider.getProxy().getAdditional()));
        merged.putAll(safetyPut(proxy.getOptions()));
        merged.putAll(safetyPut(terminal.getOptions()));
        return merged;
    }

    private static Map<String, String> safetyPut(Map<String, String> options) {
        return Optional.ofNullable(options)
                .filter(map -> !map.isEmpty())
                .orElse(new HashMap<>());
    }
}
