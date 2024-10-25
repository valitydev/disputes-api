package dev.vality.disputes.admin.callback;

import dev.vality.disputes.admin.DisputeAlreadyCreated;
import dev.vality.disputes.admin.DisputeFailedReviewRequired;
import dev.vality.disputes.admin.DisputePoolingExpired;
import dev.vality.disputes.admin.DisputeReadyForCreateAdjustment;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DisputesTgBotDefaultCallbackNotifierImpl implements DefaultCallbackNotifier {

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
    public void sendDisputeReadyForCreateAdjustment(List<Dispute> disputes) {
        var disputeReadyForCreateAdjustments = disputes.stream()
                .map(Dispute::getId)
                .map(UUID::toString)
                .map(DisputeReadyForCreateAdjustment::new)
                .toList();
        disputesTgBotService.sendDisputeReadyForCreateAdjustment(disputeReadyForCreateAdjustments);
    }

    @Override
    public void sendDisputeFailedReviewRequired(Dispute dispute, String errorCode, String errorDescription) {
        disputesTgBotService.sendDisputeFailedReviewRequired(
                new DisputeFailedReviewRequired(dispute.getId().toString(), errorCode)
                        .setErrorDescription(errorDescription));
    }
}
