package dev.vality.disputes.config;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.auth.utils.JwtTokenBuilder;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import dev.vality.disputes.schedule.service.config.CreatedFlowHandler;
import dev.vality.disputes.schedule.service.config.MerchantApiMvcPerformer;
import dev.vality.disputes.schedule.service.config.PendingFlowHnadler;
import dev.vality.disputes.schedule.service.config.WiremockAddressesHolder;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SuppressWarnings({"LineLength"})
public abstract class AbstractMockitoConfig {

    @MockitoBean
    protected InvoicingSrv.Iface invoicingClient;
    @MockitoBean
    protected TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockitoBean
    protected ArbiterSrv.Iface bouncerClient;
    @MockitoBean
    protected FileStorageSrv.Iface fileStorageClient;
    @MockitoBean
    protected DominantService dominantService;
    @MockitoBean
    protected DominantAsyncService dominantAsyncService;
    @MockitoBean
    protected PartyManagementService partyManagementService;
    @MockitoBean
    protected ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    @MockitoBean
    protected ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

    @Autowired
    protected DisputeDao disputeDao;
    @Autowired
    protected JwtTokenBuilder tokenBuilder;
    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected WiremockAddressesHolder wiremockAddressesHolder;
    @Autowired
    protected CreatedDisputesService createdDisputesService;
    @Autowired
    protected PendingDisputesService pendingDisputesService;

    protected MerchantApiMvcPerformer merchantApiMvcPerformer;
    protected CreatedFlowHandler createdFlowHandler;
    protected PendingFlowHnadler pendingFlowHnadler;

    @BeforeEach
    void setUp() {
        merchantApiMvcPerformer = new MerchantApiMvcPerformer(invoicingClient, tokenKeeperClient, bouncerClient, fileStorageClient, dominantAsyncService, partyManagementService, tokenBuilder, wiremockAddressesHolder, mvc);
        createdFlowHandler = new CreatedFlowHandler(invoicingClient, fileStorageClient, disputeDao, dominantService, createdDisputesService, providerDisputesThriftInterfaceBuilder, providerPaymentsThriftInterfaceBuilder, wiremockAddressesHolder, merchantApiMvcPerformer);
        pendingFlowHnadler = new PendingFlowHnadler(disputeDao, createdFlowHandler, pendingDisputesService, providerDisputesThriftInterfaceBuilder, providerPaymentsThriftInterfaceBuilder);
    }
}
