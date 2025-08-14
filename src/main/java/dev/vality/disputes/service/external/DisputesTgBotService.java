package dev.vality.disputes.service.external;

import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;

public interface DisputesTgBotService {

    DisputeCreatedResult createDispute(DisputeParams disputeParams);

    void notify(Dispute disputeThrift);

}
