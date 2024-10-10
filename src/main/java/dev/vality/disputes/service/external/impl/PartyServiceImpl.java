package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.Party;
import dev.vality.damsel.domain.Shop;
import dev.vality.damsel.payment_processing.PartyRevisionParam;
import dev.vality.disputes.service.external.PartyService;
import dev.vality.disputes.service.external.impl.partymgnt.PartyManagementCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyServiceImpl implements PartyService {

    private final PartyManagementCacheServiceImpl partyManagementCacheService;

    @Override
    public Shop getShop(String partyId, String shopId) {
        return partyManagementCacheService.getShop(partyId, shopId);
    }

    @Override
    public Party getParty(String partyId) {
        return partyManagementCacheService.getParty(partyId);
    }

    @Override
    public Party getParty(String partyId, long partyRevision) {
        return partyManagementCacheService.getParty(partyId, partyRevision);
    }

    @Override
    public Party getParty(String partyId, PartyRevisionParam partyRevisionParam) {
        return partyManagementCacheService.getParty(partyId, partyRevisionParam);
    }

    @Override
    public long getPartyRevision(String partyId) {
        return partyManagementCacheService.getPartyRevision(partyId);
    }
}
