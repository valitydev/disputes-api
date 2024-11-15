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
        disputesTgBotService.sendDisputeAlreadyCreated(getAlreadyCreated(dispute));
    }

    @Override
    public void sendDisputePoolingExpired(Dispute dispute) {
        disputesTgBotService.sendDisputePoolingExpired(getPoolingExpired(dispute));
    }

    @Override
    public void sendDisputeReadyForCreateAdjustment(Dispute dispute) {
        disputesTgBotService.sendDisputeReadyForCreateAdjustment(getCreateAdjustment(dispute));
    }

    @Override
    public void sendDisputeManualPending(Dispute dispute, String errorMessage) {
        disputesTgBotService.sendDisputeManualPending(getManualPending(dispute).setErrorMessage(errorMessage));
    }

    @Override
    public void sendForgottenDisputes(List<Dispute> disputes) {
        var notifications = disputes.stream()
                .map(dispute -> switch (dispute.getStatus()) {
                    case manual_pending -> Notification.disputeManualPending(getManualPending(dispute));
                    case already_exist_created -> Notification.disputeAlreadyCreated(getAlreadyCreated(dispute));
                    case create_adjustment -> Notification.disputeReadyForCreateAdjustment(
                            getCreateAdjustment(dispute));
                    case pooling_expired -> Notification.disputePoolingExpired(getPoolingExpired(dispute));
                    default -> null;
                })
                .filter(Objects::nonNull)
                .toList();
        disputesTgBotService.sendForgottenDisputes(notifications);
    }

    private DisputeManualPending getManualPending(Dispute dispute) {
        return new DisputeManualPending(dispute.getId().toString(), dispute.getInvoiceId(), dispute.getPaymentId())
                .setErrorMessage(dispute.getErrorMessage());
    }

    private DisputeAlreadyCreated getAlreadyCreated(Dispute dispute) {
        return new DisputeAlreadyCreated(dispute.getId().toString(), dispute.getInvoiceId(), dispute.getPaymentId());
    }

    private DisputeReadyForCreateAdjustment getCreateAdjustment(Dispute dispute) {
        return new DisputeReadyForCreateAdjustment(dispute.getId().toString(), dispute.getInvoiceId(), dispute.getPaymentId());
    }

    private DisputePoolingExpired getPoolingExpired(Dispute dispute) {
        return new DisputePoolingExpired(dispute.getId().toString(), dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
