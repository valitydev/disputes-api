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
public class WoodyRuntimeExceptionCatcher {

    private final ExternalGatewayChecker externalGatewayChecker;

    public void catchProviderDisputesApiNotExist(ProviderData providerData, Runnable runnable,
                                                 Runnable defaultRemoteClientRunnable) {
        try {
            runnable.run();
        } catch (WRuntimeException ex) {
            if (externalGatewayChecker.isProviderDisputesApiNotExist(providerData, ex)) {
                log.info("Trying to call defaultRemoteClient.createDispute() by case remoteClient.createDispute()==404",
                        ex);
                defaultRemoteClientRunnable.run();
                return;
            }
            throw ex;
        }
    }

    public void catchUnexpectedResultMapping(Runnable runnable,
                                             Consumer<WRuntimeException> unexpectedResultMappingHandler) {
        try {
            runnable.run();
        } catch (WRuntimeException ex) {
            if (externalGatewayChecker.isProviderDisputesUnexpectedResultMapping(ex)) {
                unexpectedResultMappingHandler.accept(ex);
                return;
            }
            throw ex;
        }
    }
}
