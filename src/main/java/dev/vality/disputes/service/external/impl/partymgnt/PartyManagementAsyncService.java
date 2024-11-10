package dev.vality.disputes.service.external.impl.partymgnt;

import dev.vality.damsel.domain.Shop;
import dev.vality.disputes.service.external.PartyManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyManagementAsyncService {

    private final PartyManagementService partyManagementService;

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<Shop> getShop(String partyId, String shopId) {
        try {
            var shop = partyManagementService.getShop(partyId, shopId);
            return CompletableFuture.completedFuture(shop);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
}
