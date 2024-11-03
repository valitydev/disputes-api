package dev.vality.disputes.callback;

import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderPaymentsErrorResultHandler {

    private final ProviderCallbackDao providerCallbackDao;

    @Transactional
    public void updateFailed(ProviderCallback providerCallback, String errorReason) {
        log.error("Trying to set failed ProviderCallback status with {} error reason {}", errorReason, providerCallback.getInvoiceId());
        providerCallback.setStatus(ProviderPaymentsStatus.failed);
        providerCallback.setErrorReason(errorReason);
        providerCallbackDao.update(providerCallback);
        log.debug("ProviderCallback status has been set to failed {}", providerCallback.getInvoiceId());
    }
}
