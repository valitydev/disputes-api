package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.Party;
import dev.vality.damsel.domain.Shop;
import dev.vality.damsel.payment_processing.InvalidPartyRevision;
import dev.vality.damsel.payment_processing.PartyManagementSrv;
import dev.vality.damsel.payment_processing.PartyNotFound;
import dev.vality.damsel.payment_processing.PartyRevisionParam;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.PartyException;
import dev.vality.disputes.service.external.PartyManagementService;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PartyManagementServiceImpl implements PartyManagementService {

    private final PartyManagementSrv.Iface partyManagementClient;

    @Autowired
    public PartyManagementServiceImpl(PartyManagementSrv.Iface partyManagementClient) {
        this.partyManagementClient = partyManagementClient;
    }

    @Override
    public Shop getShop(String partyId, String shopId) {
        log.info("Trying to get shop, partyId='{}', shopId='{}'", partyId, shopId);
        var party = getParty(partyId);
        var shop = party.getShops().get(shopId);
        if (shop == null) {
            throw new NotFoundException(
                    String.format("Shop not found, partyId='%s', shopId='%s'", partyId, shopId));
        }
        log.info("Shop has been found, partyId='{}', shopId='{}'", partyId, shopId);
        return shop;
    }

    @Override
    public Party getParty(String partyId) {
        return getParty(partyId, getPartyRevision(partyId));
    }

    @Override
    public Party getParty(String partyId, long partyRevision) {
        return getParty(partyId, PartyRevisionParam.revision(partyRevision));
    }

    @Override
    public Party getParty(String partyId, PartyRevisionParam partyRevisionParam) {
        log.info("Trying to get party, partyId='{}', partyRevisionParam='{}'", partyId, partyRevisionParam);
        try {
            var party = partyManagementClient.checkout(partyId, partyRevisionParam);
            log.info("Party has been found, partyId='{}', partyRevisionParam='{}'", partyId, partyRevisionParam);
            return party;
        } catch (PartyNotFound ex) {
            throw new NotFoundException(
                    String.format("Party not found, partyId='%s', partyRevisionParam='%s'", partyId,
                            partyRevisionParam), ex
            );
        } catch (InvalidPartyRevision ex) {
            throw new NotFoundException(
                    String.format("Invalid party revision, partyId='%s', partyRevisionParam='%s'", partyId,
                            partyRevisionParam), ex
            );
        } catch (TException ex) {
            throw new PartyException(
                    String.format("Failed to get party, partyId='%s', partyRevisionParam='%s'", partyId,
                            partyRevisionParam), ex
            );
        }
    }

    @Override
    public long getPartyRevision(String partyId) {
        try {
            log.info("Trying to get revision, partyId='{}'", partyId);
            var revision = partyManagementClient.getRevision(partyId);
            log.info("Revision has been found, partyId='{}', revision='{}'", partyId, revision);
            return revision;
        } catch (PartyNotFound ex) {
            throw new NotFoundException(String.format("Party not found, partyId='%s'", partyId), ex);
        } catch (TException ex) {
            throw new PartyException(String.format("Failed to get party revision, partyId='%s'", partyId), ex);
        }
    }
}
