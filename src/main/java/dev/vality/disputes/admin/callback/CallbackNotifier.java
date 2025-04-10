package dev.vality.disputes.admin.callback;

import dev.vality.disputes.domain.tables.pojos.Dispute;

public interface CallbackNotifier {

    void sendDisputeAlreadyCreated(Dispute dispute);

    void sendDisputePoolingExpired(Dispute dispute);

    void sendDisputeManualPending(Dispute dispute, String errorMessage);

}
