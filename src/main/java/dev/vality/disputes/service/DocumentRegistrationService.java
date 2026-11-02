package dev.vality.disputes.service;

import com.bg.candlekeep.base.DisputeDetails;
import com.bg.candlekeep.base.DocumentKind;
import com.bg.candlekeep.base.DocumentSubject;
import com.bg.candlekeep.base.InvalidRequest;
import com.bg.candlekeep.base.PaymentSubject;
import com.bg.candlekeep.base.ServiceSource;
import com.bg.candlekeep.registry.DocumentRegistrySrv;
import com.bg.candlekeep.registry.RegisterDocumentRequest;
import dev.vality.disputes.config.properties.DocumentRegistrationProperties;
import dev.vality.disputes.dao.AttachmentDocumentDao;
import dev.vality.disputes.domain.tables.pojos.AttachmentDocument;
import dev.vality.disputes.util.RemoteErrors;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.failed;
import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.pending;
import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.succeeded;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRegistrationService {

    private final AttachmentDocumentDao attachmentDocumentDao;
    private final DocumentRegistrySrv.Iface candlekeepClient;
    private final DocumentRegistrationProperties properties;

    public List<String> findPending() {
        return attachmentDocumentDao.findPending(properties.getBatchSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void registerDocument(String fileId) throws TException {
        var selected = attachmentDocumentDao.getPendingSkipLocked(fileId);
        if (selected.isEmpty()) {
            return;
        }
        var document = selected.get();
        if (document.getAttemptCount() >= properties.getMaxAttempts()) {
            document.setStatus(failed);
            document.setLastError("Document registration attempt limit reached: " + properties.getMaxAttempts());
        } else {
            document.setAttemptCount(document.getAttemptCount() + 1);
            try {
                var response = candlekeepClient.registerDocument(toRequest(document));
                document.setStatus(succeeded);
                document.setRegistryDocumentId(response.getDocumentId());
                document.setLastError(null);
                log.info("Registered dispute document, fileId={}, documentId={}", fileId, response.getDocumentId());
            } catch (TException | WRuntimeException error) {
                if (RemoteErrors.containsInterruption(error)) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
                document.setStatus(error instanceof InvalidRequest
                        || document.getAttemptCount() >= properties.getMaxAttempts() ? failed : pending);
                document.setLastError(error.getClass().getSimpleName() + ": " + error.getMessage());
                log.warn("Dispute document registration failed, fileId={}, attempt={}, status={}",
                        fileId, document.getAttemptCount(), document.getStatus(), error);
            }
        }
        var now = LocalDateTime.now(ZoneOffset.UTC);
        document.setUpdatedAt(now);
        if (document.getStatus() == pending) {
            document.setNextAttemptAfter(now.plus(properties.getRetryDelay()));
        }
        // A failed local update must roll back, allowing replay with the same idempotency key (file_data_id).
        attachmentDocumentDao.update(document);
    }

    private RegisterDocumentRequest toRequest(AttachmentDocument document) {
        return new RegisterDocumentRequest()
                .setSource(new ServiceSource("disputes-api"))
                .setKind(DocumentKind.dispute(new DisputeDetails().setDisputeId(document.getDisputeId().toString())))
                .setSubject(DocumentSubject.payment(
                        new PaymentSubject(document.getInvoiceId(), document.getPaymentId())))
                .setFileDataId(document.getFileId())
                .setSha256(document.getSha256())
                .setSizeBytes(document.getSizeBytes())
                .setMimeType(document.getMimeType())
                .setUploadedAt(document.getFileUploadedAt().toInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS).toString());
    }
}
