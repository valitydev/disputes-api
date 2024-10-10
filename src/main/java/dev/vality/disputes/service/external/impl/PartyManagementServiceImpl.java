package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.Shop;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.partymgnt.PartyManagementCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyManagementServiceImpl implements PartyManagementService {

    private final PartyManagementCacheServiceImpl partyManagementCacheService;

    @Override
    public Shop getShop(String partyId, String shopId) {
        return partyManagementCacheService.getShop(partyId, shopId);
    }
}
