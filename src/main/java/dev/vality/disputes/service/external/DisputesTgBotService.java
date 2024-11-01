package dev.vality.disputes.service.external;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;

import java.util.List;

public interface DisputesTgBotService {

    DisputeCreatedResult createDispute(DisputeParams disputeParams);

    void sendDisputeAlreadyCreated(DisputeAlreadyCreated disputeAlreadyCreated);

    void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired);

    void sendDisputeReadyForCreateAdjustment(DisputeReadyForCreateAdjustment disputeReadyForCreateAdjustment);

    void sendDisputeFailedReviewRequired(DisputeFailedReviewRequired disputeFailedReviewRequired);

    void sendForgottenDisputes(List<Notification> notifications);

}
