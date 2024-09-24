package dev.vality.disputes.schedule.service.config;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class DisputeApiTestConfig {

    @MockBean
    private InvoicingSrv.Iface invoicingClient;
    @MockBean
    private TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockBean
    private ArbiterSrv.Iface bouncerClient;
    @MockBean
    private DominantAsyncService dominantAsyncService;
    @MockBean
    private FileStorageSrv.Iface fileStorageClient;

}
