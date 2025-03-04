package dev.vality.disputes.util;

import dev.vality.damsel.domain.Provider;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_MAX_TIME_POLLING_MIN;

@UtilityClass
public class OptionsExtractor {

    public static Integer extractMaxTimePolling(Map<String, String> options, int maxTimePolling) {
        return Integer.parseInt(
                options.getOrDefault(DISPUTE_FLOW_MAX_TIME_POLLING_MIN, String.valueOf(maxTimePolling)));
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
                .orElse(new HashMap<>());
    }
}
