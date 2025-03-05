package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.createDisputeStatusSuccessResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"LineLength"})
@RequiredArgsConstructor
public class PendingFlowHandler {

    private final DisputeDao disputeDao;
    private final ProviderCallbackDao providerCallbackDao;
    private final CreatedFlowHandler createdFlowHandler;
    private final PendingDisputesService pendingDisputesService;
    private final ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

    @SneakyThrows
    public UUID handlePending() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusSuccessResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(new PaymentStatusResult(true));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        assertEquals(ProviderPaymentsStatus.create_adjustment, providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId()).getStatus());
        return disputeId;
    }
}
