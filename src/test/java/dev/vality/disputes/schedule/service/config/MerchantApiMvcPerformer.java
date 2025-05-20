package dev.vality.disputes.schedule.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WiremockAddressesHolder;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.OpenApiUtil;
import dev.vality.disputes.util.WiremockUtils;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static dev.vality.disputes.util.MockUtil.*;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@RequiredArgsConstructor
public class MerchantApiMvcPerformer {

    private final InvoicingSrv.Iface invoicingClient;
    private final TokenAuthenticatorSrv.Iface tokenKeeperClient;
    private final ArbiterSrv.Iface bouncerClient;
    private final FileStorageSrv.Iface fileStorageClient;
    private final DominantAsyncService dominantAsyncService;
    private final PartyManagementService partyManagementService;
    private final WiremockAddressesHolder wiremockAddressesHolder;
    private final MockMvc mvc;

    @SneakyThrows
    public Create200Response createDispute(String invoiceId, String paymentId) {
        when(invoicingClient.get(any(), any())).thenReturn(MockUtil.createInvoice(invoiceId, paymentId));
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
                        .content(OpenApiUtil.getContentCreateRequest(invoiceId, paymentId,
                                wiremockAddressesHolder.getNotificationUrl())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.disputeId").isNotEmpty());
        return new ObjectMapper().readValue(resultActions.andReturn().getResponse().getContentAsString(),
                Create200Response.class);
    }
}
