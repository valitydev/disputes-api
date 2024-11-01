package dev.vality.disputes.admin.callback;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DisputesTgBotCallbackNotifierImpl implements CallbackNotifier {

    private final DisputesTgBotService disputesTgBotService;

    @Override
    public void sendDisputeAlreadyCreated(Dispute dispute) {
        disputesTgBotService.sendDisputeAlreadyCreated(new DisputeAlreadyCreated(dispute.getId().toString()));
    }

    @Override
    public void sendDisputePoolingExpired(Dispute dispute) {
        disputesTgBotService.sendDisputePoolingExpired(new DisputePoolingExpired(dispute.getId().toString()));
    }

    @Override
    public void sendDisputeReadyForCreateAdjustment(Dispute dispute) {
        disputesTgBotService.sendDisputeReadyForCreateAdjustment(new DisputeReadyForCreateAdjustment(dispute.getId().toString()));
    }

    @Override
    public void sendDisputeFailedReviewRequired(Dispute dispute, String errorCode, String errorDescription) {
        disputesTgBotService.sendDisputeFailedReviewRequired(
                new DisputeFailedReviewRequired(dispute.getId().toString(), errorCode)
                        .setErrorDescription(errorDescription));
    }

    @Override
    public void sendForgottenDisputes(List<Dispute> disputes) {
        var notifications = disputes.stream()
                .map(dispute -> switch (dispute.getStatus()) {
                    case manual_created ->
                            Notification.disputeManualCreated(new DisputeManualCreated(dispute.getId().toString()).setErrorMessage(dispute.getErrorMessage()));
                    case manual_pending ->
                            Notification.disputeManualPending(new DisputeManualPending(dispute.getId().toString()).setErrorMessage(dispute.getErrorMessage()));
                    case already_exist_created ->
                            Notification.disputeAlreadyCreated(new DisputeAlreadyCreated(dispute.getId().toString()));
                    case create_adjustment ->
                            Notification.disputeReadyForCreateAdjustment(new DisputeReadyForCreateAdjustment(dispute.getId().toString()));
                    default -> null;
                })
                .filter(Objects::nonNull)
                .toList();
        disputesTgBotService.sendForgottenDisputes(notifications);
    }
}
