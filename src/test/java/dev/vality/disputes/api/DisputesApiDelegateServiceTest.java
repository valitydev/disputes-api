package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.auth.utils.JwtTokenBuilder;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.service.config.WiremockAddressesHolder;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.OpenApiUtil;
import dev.vality.disputes.util.WiremockUtils;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WireMockSpringBootITest
@SuppressWarnings({"ParameterName", "LineLength"})
@Import(WiremockAddressesHolder.class)
public class DisputesApiDelegateServiceTest {

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
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenBuilder tokenBuilder;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private WiremockAddressesHolder wiremockAddressesHolder;
    private AutoCloseable mocks;
    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[]{invoicingClient, tokenKeeperClient, bouncerClient,
                fileStorageClient, dominantAsyncService};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
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
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(createNewFileResult(wiremockAddressesHolder.getUploadUrl()));
        WiremockUtils.mockS3AttachmentUpload();
        var resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        var response = new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class);
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
        verify(dominantAsyncService, times(1)).getTerminal(any());
        verify(dominantAsyncService, times(1)).getCurrency(any());
        verify(fileStorageClient, times(1)).createNewFile(any(), any());
        mvc.perform(get("/disputes/status")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
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
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertEquals(response.getDisputeId(), new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class).getDisputeId());
        verify(invoicingClient, times(3)).get(any(), any());
        verify(tokenKeeperClient, times(3)).authenticate(any(), any());
        verify(bouncerClient, times(3)).judge(any(), any());
        disputeDao.update(UUID.fromString(response.getDisputeId()), DisputeStatus.failed);
        // new after failed
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(createNewFileResult(wiremockAddressesHolder.getUploadUrl()));
        resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertNotEquals(response.getDisputeId(), new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class).getDisputeId());
        verify(invoicingClient, times(4)).get(any(), any());
        verify(tokenKeeperClient, times(4)).authenticate(any(), any());
        verify(bouncerClient, times(4)).judge(any(), any());
        verify(dominantAsyncService, times(2)).getTerminal(any());
        verify(dominantAsyncService, times(2)).getCurrency(any());
        verify(fileStorageClient, times(2)).createNewFile(any(), any());
        disputeDao.update(UUID.fromString(response.getDisputeId()), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenInvalidCreateRequest() {
        var paymentId = "1";
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
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
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .params(OpenApiUtil.getStatusRequiredParams(randomUUID().toString(), invoiceId, paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().is4xxClientError());
    }
}
