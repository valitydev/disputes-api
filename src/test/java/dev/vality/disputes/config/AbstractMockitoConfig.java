package dev.vality.disputes.config;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import dev.vality.disputes.schedule.service.config.CreatedFlowHandler;
import dev.vality.disputes.schedule.service.config.MerchantApiMvcPerformer;
import dev.vality.disputes.schedule.service.config.PendingFlowHandler;
import dev.vality.disputes.schedule.service.config.ProviderCallbackFlowHandler;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

public abstract class AbstractMockitoConfig {

    @MockitoSpyBean
    public ProviderCallbackDao providerCallbackDao;

    @MockitoBean
    public InvoicingSrv.Iface invoicingClient;
    @MockitoBean
    public TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockitoBean
    public ArbiterSrv.Iface bouncerClient;
    @MockitoBean
    public FileStorageSrv.Iface fileStorageClient;
    @MockitoBean
    public DominantService dominantService;
    @MockitoBean
    public DominantAsyncService dominantAsyncService;
    @MockitoBean
    public PartyManagementService partyManagementService;
    @MockitoBean
    public ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    @MockitoBean
    public ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

    @Autowired
    public DisputeDao disputeDao;
    @Autowired
    public MockMvc mvc;
    @Autowired
    public WiremockAddressesHolder wiremockAddressesHolder;
    @Autowired
    public CreatedDisputesService createdDisputesService;
    @Autowired
    public PendingDisputesService pendingDisputesService;
    @Autowired
    public ProviderPaymentsService providerPaymentsService;
    @Autowired
    public ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;

    @LocalServerPort
    public int serverPort;

    public MerchantApiMvcPerformer merchantApiMvcPerformer;
    public CreatedFlowHandler createdFlowHandler;
    public PendingFlowHandler pendingFlowHandler;
    public ProviderCallbackFlowHandler providerCallbackFlowHandler;

    @BeforeEach
    void setUp() {
        merchantApiMvcPerformer =
                new MerchantApiMvcPerformer(invoicingClient, tokenKeeperClient, bouncerClient, fileStorageClient,
                        dominantAsyncService, partyManagementService, wiremockAddressesHolder, mvc);
        createdFlowHandler = new CreatedFlowHandler(invoicingClient, fileStorageClient, disputeDao, dominantService,
                createdDisputesService, providerDisputesThriftInterfaceBuilder, providerPaymentsThriftInterfaceBuilder,
                wiremockAddressesHolder, merchantApiMvcPerformer);
        pendingFlowHandler =
                new PendingFlowHandler(disputeDao, providerCallbackDao, createdFlowHandler, pendingDisputesService,
                        providerDisputesThriftInterfaceBuilder, providerPaymentsThriftInterfaceBuilder);
        providerCallbackFlowHandler =
                new ProviderCallbackFlowHandler(invoicingClient, disputeDao, providerCallbackDao, pendingFlowHandler,
                        providerPaymentsService, providerPaymentsAdjustmentExtractor);
    }
}
