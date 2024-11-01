package dev.vality.disputes.admin.callback;

import dev.vality.disputes.domain.tables.pojos.Dispute;

import java.util.List;

public interface CallbackNotifier {

    void sendDisputeAlreadyCreated(Dispute dispute);

    void sendDisputePoolingExpired(Dispute dispute);

    void sendDisputeReadyForCreateAdjustment(Dispute dispute);

    void sendDisputeFailedReviewRequired(Dispute dispute, String errorCode, String errorDescription);

    void sendForgottenDisputes(List<Dispute> disputes);

}
