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
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() {}", disputeAlreadyCreated.getInvoiceId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputeAlreadyCreated(disputeAlreadyCreated))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() has been called {}", disputeAlreadyCreated.getInvoiceId());
    }

    @Override
    @SneakyThrows
    public void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() {}", disputePoolingExpired.getInvoiceId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputePoolingExpired(disputePoolingExpired))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() has been called {}", disputePoolingExpired.getInvoiceId());
    }

    @Override
    @SneakyThrows
    public void sendDisputeReadyForCreateAdjustment(DisputeReadyForCreateAdjustment disputeReadyForCreateAdjustment) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() {}", disputeReadyForCreateAdjustment.getInvoiceId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputeReadyForCreateAdjustment(disputeReadyForCreateAdjustment))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() has been called {}", disputeReadyForCreateAdjustment.getInvoiceId());
    }

    @Override
    @SneakyThrows
    public void sendDisputeManualPending(DisputeManualPending disputeManualPending) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeManualPending() {}", disputeManualPending.getInvoiceId());
        adminCallbackDisputesTgBotClient.notify(
                new NotificationParamsRequest(List.of(Notification.disputeManualPending(disputeManualPending))));
        log.debug("adminCallbackDisputesTgBotClient.sendDisputeManualPending() has been called {}", disputeManualPending.getInvoiceId());
    }

    @Override
    @SneakyThrows
    public void sendForgottenDisputes(List<Notification> notifications) {
        log.debug("Trying to call adminCallbackDisputesTgBotClient.sendForgottenDisputes() {}", notifications.size());
        adminCallbackDisputesTgBotClient.notify(new NotificationParamsRequest(notifications));
        log.debug("adminCallbackDisputesTgBotClient.sendForgottenDisputes() has been called {}", notifications.size());
    }
}
