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
public class DummyDefaultCallbackNotifierImpl implements DefaultCallbackNotifier {

    @Override
    public void sendDisputeAlreadyCreated(Dispute dispute) {
    }

    @Override
    public void sendDisputePoolingExpired(Dispute dispute) {
    }

    @Override
    public void sendDisputeReadyForCreateAdjustment(List<Dispute> disputes) {
    }

    @Override
    public void sendDisputeFailedReviewRequired(Dispute dispute, String errorCode, String errorDescription) {
    }
}
