package dev.vality.disputes.admin.callback;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.admin.enabled", havingValue = "false")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DummyCallbackNotifierImpl implements CallbackNotifier {

    @Override
    public void sendDisputeAlreadyCreated(Dispute dispute) {
        log.debug("Trying to call DummyCallbackNotifierImpl.sendDisputeAlreadyCreated() {}", dispute.getId());
    }

    @Override
    public void sendDisputePoolingExpired(Dispute dispute) {
        log.debug("Trying to call DummyCallbackNotifierImpl.sendDisputePoolingExpired() {}", dispute.getId());
    }

    @Override
    public void sendDisputeReadyForCreateAdjustment(Dispute dispute) {
        log.debug("Trying to call DummyCallbackNotifierImpl.sendDisputeReadyForCreateAdjustment() {}", dispute.getId());
    }

    @Override
    public void sendDisputeManualPending(Dispute dispute, String errorMessage) {
        log.debug("Trying to call DummyCallbackNotifierImpl.sendDisputeManualPending() {} {}", dispute.getId(), errorMessage);
    }

    @Override
    public void sendForgottenDisputes(List<Dispute> disputes) {
        log.debug("Trying to call DummyCallbackNotifierImpl.sendForgottenDisputes() {}", disputes.size());
    }
}
