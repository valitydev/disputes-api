package dev.vality.disputes.admin.callback;

import dev.vality.disputes.admin.DisputeAlreadyCreated;
import dev.vality.disputes.admin.DisputeManualPending;
import dev.vality.disputes.admin.DisputePoolingExpired;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "service.disputes-tg-bot.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
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
    public void sendDisputeManualPending(Dispute dispute, String errorMessage) {
        disputesTgBotService.sendDisputeManualPending(getManualPending(dispute).setErrorMessage(errorMessage));
    }

    private DisputeManualPending getManualPending(Dispute dispute) {
        return new DisputeManualPending(dispute.getInvoiceId(), dispute.getPaymentId())
                .setErrorMessage(dispute.getErrorMessage());
    }

    private DisputeAlreadyCreated getAlreadyCreated(Dispute dispute) {
        return new DisputeAlreadyCreated(dispute.getInvoiceId(), dispute.getPaymentId());
    }

    private DisputePoolingExpired getPoolingExpired(Dispute dispute) {
        return new DisputePoolingExpired(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
