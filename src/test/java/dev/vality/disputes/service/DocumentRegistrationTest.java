package dev.vality.disputes.service;

import com.bg.candlekeep.base.InvalidRequest;
import com.bg.candlekeep.registry.DocumentRegistrySrv;
import com.bg.candlekeep.registry.RegisterDocumentRequest;
import com.bg.candlekeep.registry.RegisterDocumentResponse;
import dev.vality.dao.DaoException;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.ApiDisputesService;
import dev.vality.disputes.config.PostgresqlSpringBootITest;
import dev.vality.disputes.dao.AttachmentDocumentDao;
import dev.vality.disputes.domain.tables.pojos.AttachmentDocument;
import dev.vality.disputes.schedule.DocumentRegistrationTask;
import dev.vality.disputes.service.external.FileStorageService;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;
import org.apache.thrift.TException;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.failed;
import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.pending;
import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.succeeded;
import static dev.vality.disputes.domain.tables.AttachmentDocument.ATTACHMENT_DOCUMENT;
import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.FileMeta.FILE_META;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@PostgresqlSpringBootITest
@TestPropertySource(properties = "service.candlekeep.registration.max-attempts=2")
class DocumentRegistrationTest {

    private static final byte[] CONTENT = "abc".getBytes(StandardCharsets.UTF_8);
    private static final String SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Autowired
    private ApiDisputesService apiDisputesService;
    @Autowired
    private DocumentRegistrationService registrationService;
    @Autowired
    private DataSource dataSource;
    @MockitoSpyBean
    private AttachmentDocumentDao dao;
    @MockitoBean
    private FileStorageService fileStorageService;
    @MockitoBean
    private DocumentRegistrySrv.Iface candlekeepClient;

    @BeforeEach
    void setUp() {
        var counter = new AtomicInteger();
        when(fileStorageService.saveFile(any())).thenAnswer(invocation -> "file-" + counter.incrementAndGet());
    }

    @Test
    void attachmentsAndOutboxCommitOrRollBackWithDispute() {
        var beforeUpload = now().minusSeconds(1);
        var disputeId = createDispute("committed", 2);
        var documents = documents();
        assertThat(documents).hasSize(2).allSatisfy(document -> {
            assertThat(document.getDisputeId()).isEqualTo(disputeId);
            assertThat(document.getInvoiceId()).isEqualTo("committed");
            assertThat(document.getPaymentId()).isEqualTo("1");
            assertThat(document.getSha256()).isEqualTo(SHA256);
            assertThat(document.getSizeBytes()).isEqualTo(3L);
            assertThat(document.getMimeType()).isEqualTo("text/plain");
            assertThat(document.getFileUploadedAt()).isBetween(beforeUpload, now());
            assertThat(document.getNextAttemptAfter()).isEqualTo(document.getFileUploadedAt());
            assertThat(document.getStatus()).isEqualTo(pending);
            assertThat(document.getAttemptCount()).isZero();
        });
        verifyNoInteractions(candlekeepClient);

        doThrow(new DaoException("outbox unavailable")).when(dao)
                .save(argThat(document -> document.getFileId().equals("file-4")));
        assertThatThrownBy(() -> createDispute("rolled-back", 2)).isInstanceOf(DaoException.class);

        var dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        assertThat(dsl.fetchCount(DISPUTE)).isEqualTo(1);
        assertThat(dsl.fetchCount(FILE_META)).isEqualTo(2);
        assertThat(documents()).containsExactlyElementsOf(documents);
        verify(fileStorageService, times(4)).saveFile(any());
        verifyNoInteractions(candlekeepClient);
    }

    @Test
    void workerDefersTransientErrorsReplaysFailedWritesAndKeepsProcessingHealthyDocuments() throws Exception {
        final var disputeId = createDispute("registration", 4);
        when(candlekeepClient.registerDocument(any())).thenAnswer(invocation -> {
            RegisterDocumentRequest request = invocation.getArgument(0);
            request.validate();
            return switch (request.getFileDataId()) {
                case "file-1" -> throw new TException(new SocketTimeoutException("response lost"));
                case "file-2" -> throw new InvalidRequest("invalid source");
                default -> new RegisterDocumentResponse("document-" + request.getFileDataId());
            };
        });
        doThrow(new DaoException("result write unavailable")).doCallRealMethod().when(dao)
                .update(argThat(document -> document.getFileId().equals("file-4")));
        var task = new DocumentRegistrationTask(registrationService);

        task.registerDocuments();

        var documents = documents();
        assertThat(documents.get(0).getStatus()).isEqualTo(pending);
        assertThat(documents.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(documents.get(0).getNextAttemptAfter()).isAfter(now());
        assertThat(documents.get(0).getLastError()).contains("response lost");
        assertThat(documents.get(1).getStatus()).isEqualTo(failed);
        assertThat(documents.get(1).getAttemptCount()).isEqualTo(1);
        assertThat(documents.get(2).getStatus()).isEqualTo(succeeded);
        assertThat(documents.get(2).getRegistryDocumentId()).isEqualTo("document-file-3");
        assertThat(documents.get(3).getStatus()).isEqualTo(pending);
        assertThat(documents.get(3).getAttemptCount()).isZero();
        assertThat(registrationService.findPending()).containsExactly("file-4");

        task.registerDocuments();
        registrationService.registerDocument("file-2");
        registrationService.registerDocument("file-3");
        assertThat(documents().get(3).getStatus()).isEqualTo(succeeded);
        assertThat(registrationService.findPending()).isEmpty();

        DSL.using(dataSource, SQLDialect.POSTGRES).update(ATTACHMENT_DOCUMENT)
                .set(ATTACHMENT_DOCUMENT.NEXT_ATTEMPT_AFTER, now().minusSeconds(1))
                .where(ATTACHMENT_DOCUMENT.FILE_ID.eq("file-1")).execute();
        task.registerDocuments();
        assertThat(documents().getFirst().getStatus()).isEqualTo(failed);
        assertThat(documents().getFirst().getAttemptCount()).isEqualTo(2);
        assertThat(registrationService.findPending()).isEmpty();

        var requests = ArgumentCaptor.forClass(RegisterDocumentRequest.class);
        verify(candlekeepClient, times(6)).registerDocument(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getSource().getServiceId()).isEqualTo("disputes-api");
            assertThat(request.getKind().getDispute().getDisputeId()).isEqualTo(disputeId.toString());
            assertThat(request.getSubject().getPayment().getInvoiceId()).isEqualTo("registration");
            assertThat(request.getSubject().getPayment().getPaymentId()).isEqualTo("1");
            assertThat(request.getSha256()).isEqualTo(SHA256);
            assertThat(request.getSizeBytes()).isEqualTo(3L);
            assertThat(request.getMimeType()).isEqualTo("text/plain");
            assertThat(request.getUploadedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        });
        var replays = requests.getAllValues().stream()
                .filter(request -> request.getFileDataId().equals("file-4")).toList();
        assertThat(replays).hasSize(2);
        assertThat(replays.getFirst()).isEqualTo(replays.getLast());
    }

    @Test
    void competingWorkersSkipLockedDocumentAndDoNotRegisterItAgainAfterSuccess() throws Exception {
        createDispute("concurrent", 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(candlekeepClient.registerDocument(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            return new RegisterDocumentResponse("document-file-1");
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            try {
                var owner = workers.submit(() -> {
                    registrationService.registerDocument("file-1");
                    return null;
                });
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                workers.submit(() -> {
                    registrationService.registerDocument("file-1");
                    return null;
                }).get(5, TimeUnit.SECONDS);
                assertThat(owner.isDone()).isFalse();
                release.countDown();
                owner.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        registrationService.registerDocument("file-1");
        verify(candlekeepClient).registerDocument(any());
        assertThat(documents().getFirst().getStatus()).isEqualTo(succeeded);
    }

    private UUID createDispute(String invoiceId, int attachmentCount) {
        var request = new CreateRequest(invoiceId, "1", Collections.nCopies(attachmentCount,
                new CreateRequestAttachmentsInner(CONTENT, "text/plain")));
        var payment = PaymentParams.builder().invoiceId(invoiceId).paymentId("1")
                .providerId(1).terminalId(2).providerTrxId("transaction")
                .invoiceAmount(100L).options(Map.of()).build();
        return apiDisputesService.createDispute(request, payment);
    }

    private List<AttachmentDocument> documents() {
        return DSL.using(dataSource, SQLDialect.POSTGRES).selectFrom(ATTACHMENT_DOCUMENT)
                .orderBy(ATTACHMENT_DOCUMENT.FILE_ID).fetchInto(AttachmentDocument.class);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
