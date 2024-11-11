package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.createDisputeStatusSuccessResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestComponent
@Import({CreatedDisputesTestService.class})
@SuppressWarnings({"LineLength"})
public class PendingDisputesTestService {

    @Autowired
    private ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    @Autowired
    private ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private PendingDisputesService pendingDisputesService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;

    @SneakyThrows
    public UUID callPendingDisputeRemotely() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusSuccessResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(new PaymentStatusResult(true));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        return disputeId;
    }
}
