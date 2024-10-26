package dev.vality.disputes.util;

import dev.vality.bouncer.ctx.ContextFragment;
import dev.vality.bouncer.decisions.Judgement;
import dev.vality.bouncer.decisions.Resolution;
import dev.vality.bouncer.decisions.ResolutionAllowed;
import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.TerminalOptionsField;
import dev.vality.disputes.provider.*;
import dev.vality.file.storage.NewFileResult;
import dev.vality.geck.common.util.TypeUtil;
import dev.vality.token.keeper.AuthData;
import dev.vality.token.keeper.AuthDataStatus;
import dev.vality.woody.api.flow.error.WErrorDefinition;
import dev.vality.woody.api.flow.error.WErrorSource;
import dev.vality.woody.api.flow.error.WErrorType;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.thrift.TSerializer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@UtilityClass
public class MockUtil {

    public static Invoice createInvoice(String invoiceId, String paymentId) {
        return new Invoice()
                .setInvoice(new dev.vality.damsel.domain.Invoice()
                        .setId(invoiceId)
                        .setCreatedAt(TypeUtil.temporalToString(LocalDateTime.now()))
                        .setDue(TypeUtil.temporalToString(LocalDateTime.now().plusDays(1)))
                        .setDetails(new InvoiceDetails()
                                .setProduct("test_product"))
                        .setCost(new Cash().setCurrency(new CurrencyRef().setSymbolicCode("RUB"))))
                .setPayments(List.of(createInvoicePayment(paymentId)));
    }

    public static InvoicePayment createInvoicePayment(String paymentId) {
        return new InvoicePayment()
                .setPayment(new dev.vality.damsel.domain.InvoicePayment()
                        .setId(paymentId)
                        .setCreatedAt(TypeUtil.temporalToString(LocalDateTime.now()))
                        .setPayer(Payer.payment_resource(new PaymentResourcePayer()
                                .setContactInfo(DamselUtil.fillRequiredTBaseObject(new ContactInfo(),
                                        ContactInfo.class))
                                .setResource(new DisposablePaymentResource()
                                        .setPaymentTool(
                                                PaymentTool.bank_card(DamselUtil.fillRequiredTBaseObject(new BankCard(),
                                                        BankCard.class))))))
                        .setCost(new Cash()
                                .setCurrency(new CurrencyRef().setSymbolicCode("RUB")))
                        .setStatus(InvoicePaymentStatus.pending(new InvoicePaymentPending())))
                .setRoute(new PaymentRoute()
                        .setProvider(DamselUtil.fillRequiredTBaseObject(new ProviderRef(), ProviderRef.class))
                        .setTerminal(DamselUtil.fillRequiredTBaseObject(new TerminalRef(), TerminalRef.class)))
                .setLastTransactionInfo(new TransactionInfo("trxId", Map.of()));
    }

    @SneakyThrows
    public static ContextFragment createContextFragment() {
        ContextFragment fragment = DamselUtil.fillRequiredTBaseObject(new ContextFragment(), ContextFragment.class);
        fragment.setContent(new TSerializer().serialize(new dev.vality.bouncer.context.v1.ContextFragment()));
        return fragment;
    }

    public static Judgement createJudgementAllowed() {
        Resolution resolution = new Resolution();
        resolution.setAllowed(new ResolutionAllowed());
        return new Judgement().setResolution(resolution);
    }

    public static CompletableFuture<Provider> createProvider() {
        return CompletableFuture.completedFuture(new Provider()
                .setName("propropro")
                .setDescription("pepepepe")
                .setProxy(new Proxy().setRef(new ProxyRef().setId(1))));
    }

    public static CompletableFuture<ProxyDefinition> createProxy() {
        return createProxy("http://127.0.0.1:8023");
    }

    public static CompletableFuture<ProxyDefinition> createProxy(Integer port) {
        return createProxy("http://127.0.0.1:" + port + "/debug/disputes-api/admin-management");
    }

    public static CompletableFuture<ProxyDefinition> createProxyWithRealAddress(Integer port) {
        return createProxy("http://127.0.0.1:" + port);
    }

    public static CompletableFuture<ProxyDefinition> createProxy(String url) {
        return CompletableFuture.completedFuture(new ProxyDefinition()
                .setName("prprpr")
                .setDescription("pepepepe")
                .setUrl(url));
    }

    public static CompletableFuture<Terminal> createTerminal() {
        return CompletableFuture.completedFuture(new Terminal()
                .setName("prprpr")
                .setDescription("pepepepe")
                .setOptions(new HashMap<>()));
    }

    public static Map<String, String> getOptions() {
        Map<String, String> options = new HashMap<>();
        options.put(TerminalOptionsField.DISPUTE_FLOW_MAX_TIME_POLLING_MIN, "5");
        options.put(TerminalOptionsField.DISPUTE_FLOW_PROVIDERS_API_EXIST, "true");
        return options;
    }

    public static CompletableFuture<Currency> createCurrency() {
        return CompletableFuture.completedFuture(new Currency()
                .setName("Ruble")
                .setSymbolicCode("RUB")
                .setExponent((short) 2)
                .setNumericCode((short) 643));
    }

    public static CompletableFuture<Shop> createShop() {
        return CompletableFuture.completedFuture(new Shop()
                .setId("sjop_id")
                .setDetails(new ShopDetails("shop_details_name"))
        );
    }

    public static AuthData createAuthData() {
        return new AuthData()
                .setId(UUID.randomUUID().toString())
                .setAuthority(UUID.randomUUID().toString())
                .setToken(UUID.randomUUID().toString())
                .setStatus(AuthDataStatus.active)
                .setContext(createContextFragment());
    }

    public static NewFileResult createNewFileResult(String uploadUrl) {
        return new NewFileResult(UUID.randomUUID().toString(), uploadUrl);
    }

    public static DisputeCreatedResult createDisputeCreatedSuccessResult(String providerDisputeId) {
        return DisputeCreatedResult.successResult(new DisputeCreatedSuccessResult(providerDisputeId));
    }

    public static DisputeCreatedResult createDisputeCreatedFailResult() {
        return DisputeCreatedResult.failResult(new DisputeCreatedFailResult(createFailure()));
    }

    public static DisputeCreatedResult createDisputeAlreadyExistResult() {
        return DisputeCreatedResult.alreadyExistResult(new DisputeAlreadyExistResult());
    }

    public static DisputeStatusResult createDisputeStatusSuccessResult() {
        return DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult().setChangedAmount(100));
    }

    public static DisputeStatusResult createDisputeStatusFailResult() {
        return DisputeStatusResult.statusFail(new DisputeStatusFailResult(createFailure()));
    }

    public static DisputeStatusResult createDisputeStatusPendingResult() {
        return DisputeStatusResult.statusPending(new DisputeStatusPendingResult());
    }

    public static InvoicePaymentAdjustment getInvoicePaymentAdjustment(String adjustmentId, String reason) {
        return new InvoicePaymentAdjustment()
                .setId(adjustmentId)
                .setState(InvoicePaymentAdjustmentState.status_change(new InvoicePaymentAdjustmentStatusChangeState()
                        .setScenario(new InvoicePaymentAdjustmentStatusChange()
                                .setTargetStatus(new InvoicePaymentStatus(InvoicePaymentStatus.captured(
                                        new InvoicePaymentCaptured()
                                                .setReason(reason)))))));
    }

    public static Failure createFailure() {
        Failure failure = new Failure("some_error");
        failure.setSub(new SubFailure("some_suberror"));
        return failure;
    }

    public static WRuntimeException getUnexpectedResultWException() {
        var errorDefinition = new WErrorDefinition(WErrorSource.EXTERNAL);
        errorDefinition.setErrorReason("Unexpected result, code = resp_status_error, description = Tek seferde en fazla 4,000.00 işem yapılabilir.");
        errorDefinition.setErrorType(WErrorType.UNEXPECTED_ERROR);
        errorDefinition.setErrorSource(WErrorSource.INTERNAL);
        return new WRuntimeException(errorDefinition);
    }
}
