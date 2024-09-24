package dev.vality.disputes.api;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.auth.utils.JwtTokenBuilder;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.testutil.MockUtil;
import dev.vality.disputes.testutil.OpenApiUtil;
import dev.vality.file.storage.FileStorageSrv;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WireMockSpringBootITest
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
    void getDestinationsRequestSuccess() {
        when(invoicingClient.get(any(), any()))
                .thenReturn(MockUtil.createInvoice("20McecNnWoy", "1"));
        when(tokenKeeperClient.authenticate(any(), any())).thenReturn(createAuthData());
        when(bouncerClient.judge(any(), any())).thenReturn(createJudgementAllowed());
        when(dominantAsyncService.getTerminal(any())).thenReturn(createTerminal());
        when(dominantAsyncService.getCurrency(any())).thenReturn(createCurrency());
        when(fileStorageClient.createNewFile(any(), any())).thenReturn(createNewFileResult());
        when(httpClient.execute(any(), any(BasicHttpClientResponseHandler.class))).thenReturn("ok");
        mvc.perform(post("/disputes/create")
                        .header("Authorization", "Bearer " + tokenBuilder.generateJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OpenApiUtil.getContentCreateRequest()))
                .andDo(print())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        verify(invoicingClient, times(1)).get(any(), any());
        verify(tokenKeeperClient, times(1)).authenticate(any(), any());
        verify(bouncerClient, times(1)).judge(any(), any());
        verify(dominantAsyncService, times(1)).getTerminal(any());
        verify(dominantAsyncService, times(1)).getCurrency(any());
        verify(fileStorageClient, times(1)).createNewFile(any(), any());
        verify(httpClient, times(1)).execute(any(), any(BasicHttpClientResponseHandler.class));
    }


}