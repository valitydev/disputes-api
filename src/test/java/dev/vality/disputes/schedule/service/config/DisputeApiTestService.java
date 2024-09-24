package dev.vality.disputes.schedule.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.auth.utils.JwtTokenBuilder;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.OpenApiUtil;
import dev.vality.disputes.util.WiremockUtils;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static dev.vality.disputes.util.MockUtil.*;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestComponent
@Import({DisputeApiTestConfig.class, WiremockAddressesHolder.class})
@SuppressWarnings({"ParameterName", "LineLength"})
public class DisputeApiTestService {

    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @Autowired
    private TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @Autowired
    private ArbiterSrv.Iface bouncerClient;
    @Autowired
    private DominantAsyncService dominantAsyncService;
    @Autowired
    private FileStorageSrv.Iface fileStorageClient;
    @Autowired
    private JwtTokenBuilder tokenBuilder;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private WiremockAddressesHolder wiremockAddressesHolder;

    @SneakyThrows
    public Create200Response createDisputeViaApi(String invoiceId, String paymentId) {
        when(invoicingClient.get(any(), any())).thenReturn(MockUtil.createInvoice(invoiceId, paymentId));
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
        return new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(), Create200Response.class);
    }
}
