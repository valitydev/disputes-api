package dev.vality.disputes.schedule.service;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.service.config.CreatedDisputesTestService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.PendingDisputesTestService;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.util.WiremockUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
@SuppressWarnings({"LineLength"})
public class NotificationServiceTest {

    @Autowired
    private ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @Autowired
    private DominantService dominantService;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private NotificationDao notificationDao;
    @Autowired
    private PendingDisputesService pendingDisputesService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;
    @Autowired
    private NotificationService notificationService;

    @Test
    @SneakyThrows
    public void testDisputeStatusSuccessResult() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        WiremockUtils.mockNotificationSuccess();
        // todo providercallback flow set success
        disputeDao.finishSucceeded(disputeId, null);
        var dispute = disputeDao.get(disputeId);
        var notification = notificationDao.get(disputeId);
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build(), 5);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }
}
