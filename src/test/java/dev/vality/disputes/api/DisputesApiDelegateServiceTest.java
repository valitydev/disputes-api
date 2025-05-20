package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.config.WiremockAddressesHolder;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.OpenApiUtil;
import dev.vality.disputes.util.WiremockUtils;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.geck.common.util.TypeUtil;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WireMockSpringBootITest
public class DisputesApiDelegateServiceTest {

    @MockitoBean
    private InvoicingSrv.Iface invoicingClient;
    @MockitoBean
    private TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockitoBean
    private ArbiterSrv.Iface bouncerClient;
    @MockitoBean
    private DominantAsyncService dominantAsyncService;
    @MockitoBean
    private PartyManagementService partyManagementService;
    @MockitoBean
    private FileStorageSrv.Iface fileStorageClient;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private WiremockAddressesHolder wiremockAddressesHolder;
    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        preparedMocks = new Object[] {invoicingClient, tokenKeeperClient, bouncerClient,
                fileStorageClient, dominantAsyncService, partyManagementService};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
    }

    @Test
    @SneakyThrows
    void testFullApiFlowSuccess() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        when(invoicingClient.get(any(), any()))
                .thenReturn(MockUtil.createInvoice(invoiceId, paymentId));
        when(tokenKeeperClient.authenticate(any(), any())).thenReturn(createAuthData());
        when(bouncerClient.judge(any(), any())).thenReturn(createJudgementAllowed());
        when(dominantAsyncService.getTerminal(any())).thenReturn(createTerminal());
        when(dominantAsyncService.getCurrency(any())).thenReturn(createCurrency());
        when(dominantAsyncService.getProvider(any())).thenReturn(createProvider());
        when(dominantAsyncService.getProxy(any())).thenReturn(createProxy());
        when(partyManagementService.getShop(any(), any())).thenReturn(createShop());
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(
                createNewFileResult(wiremockAddressesHolder.getUploadUrl()));
        WiremockUtils.mockS3AttachmentUpload();
        var resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        var response = new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(),
                Create200Response.class);
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
        verify(dominantAsyncService, times(1)).getTerminal(any());
        verify(dominantAsyncService, times(1)).getCurrency(any());
        verify(dominantAsyncService, times(1)).getProvider(any());
        verify(dominantAsyncService, times(1)).getProxy(any());
        verify(partyManagementService, times(1)).getShop(any(), any());
        verify(fileStorageClient, times(1)).createNewFile(any(), any());
        mvc.perform(get("/disputes/status")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .params(OpenApiUtil.getStatusRequiredParams(response.getDisputeId(), invoiceId, paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").isNotEmpty());
        verify(invoicingClient, times(2)).get(any(), any());
        verify(tokenKeeperClient, times(2)).authenticate(any(), any());
        verify(bouncerClient, times(2)).judge(any(), any());
        // exist
        resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertEquals(response.getDisputeId(),
                new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(),
                        Create200Response.class).getDisputeId());
        verify(invoicingClient, times(3)).get(any(), any());
        verify(tokenKeeperClient, times(3)).authenticate(any(), any());
        verify(bouncerClient, times(3)).judge(any(), any());
        disputeDao.finishFailed(UUID.fromString(response.getDisputeId()), null);
        // new after failed
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(
                createNewFileResult(wiremockAddressesHolder.getUploadUrl()));
        resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertNotEquals(response.getDisputeId(),
                new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(),
                        Create200Response.class).getDisputeId());
        verify(invoicingClient, times(4)).get(any(), any());
        verify(tokenKeeperClient, times(4)).authenticate(any(), any());
        verify(bouncerClient, times(4)).judge(any(), any());
        verify(dominantAsyncService, times(2)).getTerminal(any());
        verify(dominantAsyncService, times(2)).getCurrency(any());
        verify(dominantAsyncService, times(2)).getProvider(any());
        verify(dominantAsyncService, times(2)).getProxy(any());
        verify(partyManagementService, times(2)).getShop(any(), any());
        verify(fileStorageClient, times(2)).createNewFile(any(), any());
        disputeDao.finishFailed(UUID.fromString(response.getDisputeId()), null);
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenInvalidCreateRequest() {
        var paymentId = "1";
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentInvalidCreateRequest(paymentId)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @SneakyThrows
    void testNotFoundWhenUnknownDisputeId() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        mvc.perform(get("/disputes/status")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .params(OpenApiUtil.getStatusRequiredParams(randomUUID().toString(), invoiceId, paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenInvalidMimeType() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        when(invoicingClient.get(any(), any()))
                .thenReturn(MockUtil.createInvoice(invoiceId, paymentId));
        when(tokenKeeperClient.authenticate(any(), any())).thenReturn(createAuthData());
        when(bouncerClient.judge(any(), any())).thenReturn(createJudgementAllowed());
        when(dominantAsyncService.getTerminal(any())).thenReturn(createTerminal());
        when(dominantAsyncService.getCurrency(any())).thenReturn(createCurrency());
        when(dominantAsyncService.getProvider(any())).thenReturn(createProvider());
        when(dominantAsyncService.getProxy(any())).thenReturn(createProxy());
        when(partyManagementService.getShop(any(), any())).thenReturn(createShop());
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentInvalidMimeTypeCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is4xxClientError());
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
        verify(dominantAsyncService, times(1)).getTerminal(any());
        verify(dominantAsyncService, times(1)).getCurrency(any());
        verify(dominantAsyncService, times(1)).getProvider(any());
        verify(dominantAsyncService, times(1)).getProxy(any());
        verify(partyManagementService, times(1)).getShop(any(), any());
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenPaymentExpired() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var invoice = createInvoice(invoiceId, paymentId);
        invoice.getPayments().getFirst().getPayment()
                .setCreatedAt(TypeUtil.temporalToString(LocalDateTime.now().minusDays(40)));
        when(invoicingClient.get(any(), any()))
                .thenReturn(invoice);
        when(tokenKeeperClient.authenticate(any(), any())).thenReturn(createAuthData());
        when(bouncerClient.judge(any(), any())).thenReturn(createJudgementAllowed());
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentInvalidMimeTypeCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is4xxClientError());
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenRequestSizeBellowLimit() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentInvalidSizeCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is4xxClientError());
    }
}
