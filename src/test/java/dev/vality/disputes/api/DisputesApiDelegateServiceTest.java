package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.auth.utils.JwtTokenBuilder;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.testutil.MockUtil;
import dev.vality.disputes.testutil.OpenApiUtil;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static dev.vality.disputes.testutil.MockUtil.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WireMockSpringBootITest
@SuppressWarnings({"ParameterName", "LineLength"})
public class DisputesApiDelegateServiceTest {

    @MockBean
    public InvoicingSrv.Iface invoicingClient;
    @MockBean
    public TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockBean
    public ArbiterSrv.Iface bouncerClient;
    @MockBean
    public DominantAsyncService dominantAsyncService;
    @MockBean
    private FileStorageSrv.Iface fileStorageClient;
    @MockBean
    private CloseableHttpClient httpClient;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenBuilder tokenBuilder;
    @Autowired
    private DisputeDao disputeDao;
    private AutoCloseable mocks;
    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[]{invoicingClient, tokenKeeperClient, bouncerClient,
                fileStorageClient, httpClient, dominantAsyncService};
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
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(createNewFileResult());
        when(httpClient.execute(any(), any(BasicHttpClientResponseHandler.class))).thenReturn("ok");
        var resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        var response = new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class);
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
        verify(dominantAsyncService, times(1)).getTerminal(any());
        verify(dominantAsyncService, times(1)).getCurrency(any());
        verify(fileStorageClient, times(1)).createNewFile(any(), any());
        verify(httpClient, times(1)).execute(any(), any(BasicHttpClientResponseHandler.class));
        mvc.perform(get("/disputes/status")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .params(OpenApiUtil.getStatusRequiredParams(response.getDisputeId(), invoiceId, paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andDo(print())
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
                .andDo(print())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertEquals(response.getDisputeId(), new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class).getDisputeId());
        verify(invoicingClient, times(3)).get(any(), any());
        verify(tokenKeeperClient, times(3)).authenticate(any(), any());
        verify(bouncerClient, times(3)).judge(any(), any());
        disputeDao.update(Long.parseLong(response.getDisputeId()), DisputeStatus.succeeded);
        // new after succeeded
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(createNewFileResult());
        resultActions = mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        assertNotEquals(response.getDisputeId(), new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class).getDisputeId());
        verify(invoicingClient, times(4)).get(any(), any());
        verify(tokenKeeperClient, times(4)).authenticate(any(), any());
        verify(bouncerClient, times(4)).judge(any(), any());
        verify(dominantAsyncService, times(2)).getTerminal(any());
        verify(dominantAsyncService, times(2)).getCurrency(any());
        verify(fileStorageClient, times(2)).createNewFile(any(), any());
        verify(httpClient, times(2)).execute(any(), any(BasicHttpClientResponseHandler.class));
    }

    @Test
    @SneakyThrows
    void testBadRequestWhenInvalidCreateRequest() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentInvalidCreateRequest(invoiceId, paymentId)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @SneakyThrows
    void testNotFoundWhenUnknownDisputeId() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = "1";
        when(invoicingClient.get(any(), any()))
                .thenReturn(MockUtil.createInvoice(invoiceId, paymentId));
        when(tokenKeeperClient.authenticate(any(), any())).thenReturn(createAuthData());
        when(bouncerClient.judge(any(), any())).thenReturn(createJudgementAllowed());
        mvc.perform(get("/disputes/status")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .params(OpenApiUtil.getStatusRequiredParams(disputeId, invoiceId, paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andDo(print())
                .andExpect(status().is4xxClientError());
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
    }
}