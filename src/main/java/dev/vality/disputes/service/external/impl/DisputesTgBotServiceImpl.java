package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DisputesTgBotServiceImpl implements DisputesTgBotService {

    public final ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient;
    public final AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient;

    @Override
    @SneakyThrows
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) {
        log.debug("Trying to call providerDisputesTgBotClient.createDispute() {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
        var invoice = providerDisputesTgBotClient.createDispute(disputeParams);
        log.debug("providerDisputesTgBotClient.createDispute() has been called {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
        return invoice;
    }

    @Override
    @SneakyThrows
    public void sendDisputeAlreadyCreated(DisputeAlreadyCreated disputeAlreadyCreated) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() {}", disputeAlreadyCreated.getId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputeAlreadyCreated(disputeAlreadyCreated))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() has been called {}", disputeAlreadyCreated.getId());
    }

    @Override
    @SneakyThrows
    public void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() {}", disputePoolingExpired.getId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputePoolingExpired(disputePoolingExpired))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() has been called {}", disputePoolingExpired.getId());
    }

    @Override
    @SneakyThrows
    public void sendDisputesReadyForCreateAdjustment(List<DisputeReadyForCreateAdjustment> disputesReadyForCreateAdjustments) {
        var ids = disputesReadyForCreateAdjustments.stream()
                .map(DisputeReadyForCreateAdjustment::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() {}", ids);
        var notifications = disputesReadyForCreateAdjustments.stream()
                .map(Notification::disputeReadyForCreateAdjustment)
                .collect(Collectors.toList());
        adminCallbackDisputesTgBotClient.notify(new NotificationParamsRequest(notifications));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() has been called {}", ids);
    }

    @Override
    @SneakyThrows
    public void sendDisputeFailedReviewRequired(DisputeFailedReviewRequired disputeFailedReviewRequired) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeFailedReviewRequired() {}", disputeFailedReviewRequired.getId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputeFailedReviewRequired(disputeFailedReviewRequired))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeFailedReviewRequired() has been called {}", disputeFailedReviewRequired.getId());
    }
}
