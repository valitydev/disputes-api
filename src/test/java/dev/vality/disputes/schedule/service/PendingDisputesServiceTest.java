package dev.vality.disputes.schedule.service;

import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.schedule.service.config.CreatedDisputesTestService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.PendingDisputesTestService;
import dev.vality.disputes.service.external.DominantService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
public class PendingDisputesServiceTest {

    @Autowired
    private ProviderIfaceBuilder providerIfaceBuilder;
    @Autowired
    private DominantService dominantService;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private PendingDisputesService pendingDisputesService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;

    @Test
    @SneakyThrows
    public void testProviderDisputeNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        disputeDao.update(disputeId, DisputeStatus.pending);
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.created, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testDisputeStatusSuccessResult() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testDisputeStatusFailResult() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusFailResult());
        when(providerIfaceBuilder.buildTHSpawnClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
    }

    @Test
    @SneakyThrows
    public void testDisputeStatusPendingResult() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusPendingResult());
        when(providerIfaceBuilder.buildTHSpawnClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }
}
