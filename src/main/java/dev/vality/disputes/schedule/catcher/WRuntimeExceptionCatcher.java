package dev.vality.disputes.schedule.catcher;

import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.service.ExternalGatewayChecker;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class WRuntimeExceptionCatcher {

    private final ExternalGatewayChecker externalGatewayChecker;

    public void catchProviderDisputesApiNotExist(ProviderData providerData, Runnable runnable, Runnable defaultRemoteClientRunnable) {
        try {
            runnable.run();
        } catch (WRuntimeException e) {
            if (externalGatewayChecker.isProviderDisputesApiNotExist(providerData, e)) {
                // отправлять на ручной разбор, если API диспутов на провайдере не реализовано
                // (тогда при тесте соединения вернется 404)
                log.warn("Trying to call defaultRemoteClient.createDispute(), externalGatewayChecker", e);
                defaultRemoteClientRunnable.run();
                return;
            }
            throw e;
        }
    }

    public void catchUnexpectedResultMapping(Runnable runnable, Consumer<WRuntimeException> unexpectedResultMappingHandler) {
        try {
            runnable.run();
        } catch (WRuntimeException e) {
            if (externalGatewayChecker.isProviderDisputesUnexpectedResultMapping(e)) {
                unexpectedResultMappingHandler.accept(e);
                return;
            }
            throw e;
        }
    }
}
