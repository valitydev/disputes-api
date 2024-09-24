package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.ProviderDisputesServiceSrv;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.service.PendingDisputesService;
import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;

import static dev.vality.disputes.util.MockUtil.createDisputeStatusSuccessResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestComponent
@Import({CreatedDisputesTestService.class})
@SuppressWarnings({"ParameterName", "LineLength"})
public class PendingDisputesTestService {

    @Autowired
    private ProviderIfaceBuilder providerIfaceBuilder;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private PendingDisputesService pendingDisputesService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;

    @SneakyThrows
    public String callPendingDisputeRemotely() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusSuccessResult());
        when(providerIfaceBuilder.buildTHSpawnClient(any(), any())).thenReturn(providerMock);
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        pendingDisputesService.callPendingDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        return disputeId;
    }
}
