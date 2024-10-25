package dev.vality.disputes.service.external;

import dev.vality.disputes.admin.DisputeAlreadyCreated;
import dev.vality.disputes.admin.DisputeFailedReviewRequired;
import dev.vality.disputes.admin.DisputePoolingExpired;
import dev.vality.disputes.admin.DisputeReadyForCreateAdjustment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;

import java.util.List;

public interface DisputesTgBotService {

    DisputeCreatedResult createDispute(DisputeParams disputeParams);

    void sendDisputeAlreadyCreated(DisputeAlreadyCreated disputeAlreadyCreated);

    void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired);

    void sendDisputeReadyForCreateAdjustment(List<DisputeReadyForCreateAdjustment> disputeReadyForCreateAdjustments);

    void sendDisputeFailedReviewRequired(DisputeFailedReviewRequired disputeFailedReviewRequired);

}
