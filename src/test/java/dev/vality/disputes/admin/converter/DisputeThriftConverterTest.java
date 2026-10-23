package dev.vality.disputes.admin.converter;

import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.service.external.FileStorageService;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TProtocolException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DisputeThriftConverterTest {

    @Test
    void shouldSerializeDownloadUrlsWithoutDownloadingFiles() throws Exception {
        var dispute = dispute();
        var files = mock(FileMetaDao.class);
        when(files.getDisputeFiles(dispute.getId())).thenReturn(List.of(
                new FileMeta("file-1", dispute.getId(), "application/pdf")));
        var storage = mock(FileStorageService.class);
        when(storage.generateDownloadUrl("file-1")).thenReturn("https://storage.example/file?signature=test");
        var http = mock(CloseableHttpClient.class);
        var providers = mock(ProviderDisputeDao.class);
        when(providers.get(dispute.getId())).thenReturn(new dev.vality.disputes.domain.tables.pojos.ProviderDispute());
        var converter = new DisputeThriftConverter(providers, files, storage, http);

        var result = converter.convert(dispute, false);
        var decoded = new dev.vality.disputes.admin.Dispute();
        new TDeserializer().deserialize(decoded, new TSerializer().serialize(result));

        var attachment = decoded.getAttachmentRefs().orElseThrow().getFirst();
        assertEquals("application/pdf", attachment.getMimeType());
        assertEquals("https://storage.example/file?signature=test", attachment.getSourceUrl());
        assertFalse(decoded.isSetAttachments());
        verifyNoInteractions(http);
    }

    @Test
    void shouldPreserveNotificationAndOtherFilesWhenUrlGenerationFails() throws Exception {
        var dispute = dispute();
        var files = mock(FileMetaDao.class);
        when(files.getDisputeFiles(dispute.getId())).thenReturn(List.of(
                new FileMeta("missing", dispute.getId(), "application/pdf"),
                new FileMeta("valid", dispute.getId(), "image/png")));
        var storage = mock(FileStorageService.class);
        when(storage.generateDownloadUrl("missing")).thenThrow(new IllegalStateException("Unavailable"));
        when(storage.generateDownloadUrl("valid")).thenReturn("https://storage.example/valid");
        var http = mock(CloseableHttpClient.class);
        var providers = mock(ProviderDisputeDao.class);
        when(providers.get(dispute.getId())).thenReturn(new dev.vality.disputes.domain.tables.pojos.ProviderDispute());
        var converter = new DisputeThriftConverter(providers, files, storage, http);

        var result = converter.convert(dispute, false);

        var attachments = result.getAttachmentRefs().orElseThrow();
        assertEquals(1, attachments.size());
        assertEquals("https://storage.example/valid", attachments.getFirst().getSourceUrl());
        var decoded = new dev.vality.disputes.admin.Dispute();
        new TDeserializer().deserialize(decoded, new TSerializer().serialize(result));
        assertEquals(attachments, decoded.getAttachmentRefs().orElseThrow());
        assertEquals(dispute.getId().toString(), result.getDisputeId());
        assertFalse(result.isSetAttachments());
        verifyNoInteractions(http);
    }

    @Test
    void shouldRejectAttachmentWithoutRequiredSourceUrl() {
        var attachment = new dev.vality.disputes.admin.AttachmentRef().setMimeType("application/pdf");

        assertThrows(TProtocolException.class, () -> new TSerializer().serialize(attachment));
    }

    private Dispute dispute() {
        var dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setInvoiceId("invoice");
        dispute.setPaymentId("payment");
        dispute.setProviderTrxId("transaction");
        dispute.setAmount(100L);
        dispute.setStatus(DisputeStatus.manual_pending);
        return dispute;
    }
}
