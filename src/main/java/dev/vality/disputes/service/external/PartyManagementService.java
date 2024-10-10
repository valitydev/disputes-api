package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.Party;
import dev.vality.damsel.domain.Shop;
import dev.vality.damsel.payment_processing.PartyRevisionParam;

public interface PartyManagementService {

    Shop getShop(String partyId, String shopId);

    Party getParty(String partyId);

    Party getParty(String partyId, long partyRevision);

    Party getParty(String partyId, PartyRevisionParam partyRevisionParam);

    long getPartyRevision(String partyId);

}
