package dev.vality.disputes.schedule.client;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.schedule.model.ProviderData;

import java.util.List;

@SuppressWarnings({"LineLength"})
public interface DefaultRemoteClient {

    Boolean routeUrlEquals(ProviderData providerData);

    DisputeCreatedResult createDispute(Dispute dispute, List<Attachment> attachments, ProviderData providerData, TransactionInfo transactionInfo);

}
