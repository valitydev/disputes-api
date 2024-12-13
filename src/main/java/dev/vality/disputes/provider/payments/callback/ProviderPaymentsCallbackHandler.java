package dev.vality.disputes.provider.payments.callback;

import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsCallbackHandler implements ProviderPaymentsCallbackServiceSrv.Iface {

    private final ProviderPaymentsService providerPaymentsService;

    @Value("${provider.payments.isProviderCallbackEnabled}")
    private boolean isProviderCallbackEnabled;

    @Override
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams callback) throws TException {
        log.info("Got providerPaymentsCallbackParams {}", callback);
        if (!isProviderCallbackEnabled) {
            return;
        }
        if (callback.getInvoiceId().isEmpty() && callback.getPaymentId().isEmpty()) {
            log.info("InvoiceId should be set, finish");
            return;
        }
        providerPaymentsService.processCallback(callback);
    }
}
