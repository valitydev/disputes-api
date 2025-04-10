package dev.vality.disputes.service.external;

import dev.vality.disputes.admin.DisputeAlreadyCreated;
import dev.vality.disputes.admin.DisputeManualPending;
import dev.vality.disputes.admin.DisputePoolingExpired;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;

public interface DisputesTgBotService {

    DisputeCreatedResult createDispute(DisputeParams disputeParams);

    void sendDisputeAlreadyCreated(DisputeAlreadyCreated disputeAlreadyCreated);

    void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired);

    void sendDisputeManualPending(DisputeManualPending disputeManualPending);

}
