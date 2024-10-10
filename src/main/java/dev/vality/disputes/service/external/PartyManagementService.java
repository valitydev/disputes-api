package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.Shop;

public interface PartyManagementService {

    Shop getShop(String partyId, String shopId);

}
