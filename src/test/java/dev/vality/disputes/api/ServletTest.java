package dev.vality.disputes.api;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.admin.AdminManagementServiceSrv;
import dev.vality.disputes.admin.CancelParamsRequest;
import dev.vality.disputes.callback.DisputeCallbackParams;
import dev.vality.disputes.callback.ProviderDisputesCallbackServiceSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.merchant.DisputeParams;
import dev.vality.disputes.merchant.MerchantDisputesServiceSrv;
import dev.vality.disputes.schedule.service.config.WiremockAddressesHolder;
import dev.vality.disputes.util.DamselUtil;
import dev.vality.woody.api.flow.error.WRuntimeException;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;

import static dev.vality.disputes.config.NetworkConfig.*;

@WireMockSpringBootITest
@SuppressWarnings({"ParameterName", "LineLength"})
@Import(WiremockAddressesHolder.class)
@TestPropertySource(properties = {
        "server.port=${local.server.port}",
})
public class ServletTest {

    @MockBean
    private InvoicingSrv.Iface invoicingClient;
    @LocalServerPort
    private int serverPort;

    @Test
    @SneakyThrows
    public void merchantServletTest() {
        var iface = new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + MERCHANT))
                .withNetworkTimeout(5000)
                .build(MerchantDisputesServiceSrv.Iface.class);
        var request = DamselUtil.fillRequiredTBaseObject(
                new DisputeParams(),
                DisputeParams.class
        );
        Assertions.assertThrows(WRuntimeException.class, () -> iface.createDispute(request));
    }

    @Test
    @SneakyThrows
    public void adminManagementServletTest() {
        var iface = new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + ADMIN_MANAGEMENT))
                .withNetworkTimeout(5000)
                .build(AdminManagementServiceSrv.Iface.class);
        var request = DamselUtil.fillRequiredTBaseObject(
                new CancelParamsRequest(),
                CancelParamsRequest.class
        );
        iface.cancelPending(request);
    }

    @Test
    @SneakyThrows
    public void callbackServletTest() {
        var iface = new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + CALLBACK))
                .withNetworkTimeout(5000)
                .build(ProviderDisputesCallbackServiceSrv.Iface.class);
        var request = DamselUtil.fillRequiredTBaseObject(
                new DisputeCallbackParams(),
                DisputeCallbackParams.class
        );
        iface.createAdjustmentIfPaymentSuccess(request);
    }

    @Test
    @SneakyThrows
    public void wrongPathServletTest() {
        var iface = new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + "/wrong_path"))
                .withNetworkTimeout(5000)
                .build(MerchantDisputesServiceSrv.Iface.class);
        var request = DamselUtil.fillRequiredTBaseObject(
                new DisputeParams(),
                DisputeParams.class
        );
        Assertions.assertThrows(WRuntimeException.class, () -> iface.createDispute(request));
    }
}
