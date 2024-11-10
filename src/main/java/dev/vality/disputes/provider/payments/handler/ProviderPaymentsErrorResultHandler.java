package dev.vality.disputes.provider.payments.handler;

import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsErrorResultHandler {

    private final ProviderCallbackDao providerCallbackDao;

    public void finishFailed(ProviderCallback providerCallback, String errorReason) {
        log.warn("Trying to set failed ProviderCallback status with {} error reason {}", errorReason, providerCallback.getInvoiceId());
        providerCallback.setStatus(ProviderPaymentsStatus.failed);
        providerCallback.setErrorReason(errorReason);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to failed {}", providerCallback.getInvoiceId());
    }
}
