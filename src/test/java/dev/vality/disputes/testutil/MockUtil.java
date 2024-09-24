package dev.vality.disputes.testutil;

import dev.vality.bouncer.ctx.ContextFragment;
import dev.vality.bouncer.decisions.Judgement;
import dev.vality.bouncer.decisions.Resolution;
import dev.vality.bouncer.decisions.ResolutionAllowed;
import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.proxy_provider.Shop;
import dev.vality.file.storage.NewFileResult;
import dev.vality.geck.common.util.TypeUtil;
import dev.vality.token.keeper.AuthData;
import dev.vality.token.keeper.AuthDataStatus;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.thrift.TSerializer;

import java.time.LocalDateTime;
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

    public static Shop createShop(String shopId) {
        var location = new ShopLocation();
        location.setUrl("http://google.com");
        return new Shop()
                .setId(shopId)
                .setLocation(location)
                .setDetails(new ShopDetails().setName("shop")
                        .setDescription("desc"))
                .setCategory(new Category().setType(CategoryType.test)
                        .setName("test")
                        .setDescription("desc"));
    }

    public static CompletableFuture<Provider> createProvider() {
        return CompletableFuture.completedFuture(new Provider()
                .setName("propropro")
                .setDescription("pepepepe")
                .setProxy(new Proxy().setRef(new ProxyRef().setId(1))));
    }

    public static CompletableFuture<ProxyDefinition> createProxy() {
        return createProxy("http://ya.ru");
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
                .setOptions(Map.of()));
    }

    public static CompletableFuture<Currency> createCurrency() {
        return CompletableFuture.completedFuture(new Currency()
                .setName("Ruble")
                .setSymbolicCode("RUB")
                .setExponent((short) 2)
                .setNumericCode((short) 643));
    }

    public static AuthData createAuthData() {
        return new AuthData()
                .setId(UUID.randomUUID().toString())
                .setAuthority(UUID.randomUUID().toString())
                .setToken(UUID.randomUUID().toString())
                .setStatus(AuthDataStatus.active)
                .setContext(createContextFragment());
    }

    public NewFileResult createNewFileResult() {
        return new NewFileResult(UUID.randomUUID().toString(), "http://localhost:8022/");
    }

    private static Failure createFailure() {
        Failure failure = new Failure("some_error");
        failure.setSub(new SubFailure("some_suberror"));
        return failure;
    }
}
