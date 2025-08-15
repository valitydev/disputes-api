package dev.vality.disputes.admin.converter;

import dev.vality.disputes.admin.Attachment;
import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.service.external.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeThriftConverter {

    private final ProviderDisputeDao providerDisputeDao;
    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;
    private final CloseableHttpClient httpClient;

    public Dispute convert(dev.vality.disputes.domain.tables.pojos.Dispute dispute, boolean withAttachments)
            throws IOException {
        var disputeThrift = new Dispute();
        disputeThrift.setDisputeId(dispute.getId().toString());
        disputeThrift.setProviderDisputeId(getProviderDispute(dispute)
                .map(ProviderDispute::getProviderDisputeId)
                .orElse(null));
        disputeThrift.setInvoiceId(dispute.getInvoiceId());
        disputeThrift.setPaymentId(dispute.getPaymentId());
        disputeThrift.setProviderTrxId(dispute.getProviderTrxId());
        disputeThrift.setStatus(dispute.getStatus().name());
        disputeThrift.setMapping(dispute.getMapping());
        disputeThrift.setAmount(String.valueOf(dispute.getAmount()));
        disputeThrift.setChangedAmount(Optional.ofNullable(dispute.getChangedAmount())
                .map(String::valueOf)
                .orElse(null));
        disputeThrift.setTechnicalErrorMessage(dispute.getTechErrorMsg());
        disputeThrift.setMode(dispute.getMode());
        disputeThrift.setProviderMessage(dispute.getProviderMsg());
        log.debug("Dispute getDispute {}", disputeThrift);
        if (!withAttachments) {
            return disputeThrift;
        }
        try {
            disputeThrift.setAttachments(new ArrayList<>());
            for (var disputeFile : fileMetaDao.getDisputeFiles(dispute.getId())) {
                var downloadUrl = fileStorageService.generateDownloadUrl(disputeFile.getFileId());
                var data = httpClient.execute(
                        new HttpGet(downloadUrl),
                        new AbstractHttpClientResponseHandler<byte[]>() {
                            @Override
                            public byte[] handleEntity(HttpEntity entity) throws IOException {
                                return EntityUtils.toByteArray(entity);
                            }
                        });
                disputeThrift.getAttachments().get().add(new Attachment()
                        .setData(data));
            }
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle AdminManagementDisputesService.getDispute, type={}", ex.getType(), ex);
        }
        return disputeThrift;
    }

    private Optional<ProviderDispute> getProviderDispute(dev.vality.disputes.domain.tables.pojos.Dispute dispute) {
        try {
            return Optional.of(providerDisputeDao.get(dispute.getId()));
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle AdminManagementDisputesService.getDispute, type={}", ex.getType(), ex);
            return Optional.empty();
        }
    }
}
