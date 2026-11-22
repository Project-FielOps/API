package com.fieldops.evidence;

import com.fieldops.evidence.application.EvidenceStorageProperties;
import com.fieldops.evidence.application.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceStorageTest {

    @Test
    void localStoragePersistsFileAndCalculatesChecksum(@TempDir Path tempDir) throws Exception {
        FileStorageService storage = new FileStorageService(
                new EvidenceStorageProperties("local", tempDir.toString(), "", ""));
        UUID inspectionId = UUID.randomUUID();
        byte[] content = "evidence".getBytes();

        var stored = storage.store(inspectionId,
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", content));

        assertEquals(content.length, stored.sizeBytes());
        assertTrue(stored.storageKey().startsWith(inspectionId + "/"));
        assertTrue(stored.storageKey().endsWith(".jpg"));
        assertTrue(Files.exists(tempDir.resolve(stored.storageKey())));
        assertEquals(64, stored.checksum().length());
        storage.delete(stored.storageKey());
        assertFalse(Files.exists(tempDir.resolve(stored.storageKey())));
    }

    @Test
    void s3StorageUsesConfiguredBucketAndObjectMetadata() {
        S3Client s3 = mock(S3Client.class);
        var storage = new com.fieldops.evidence.application.S3EvidenceStorageService(s3,
                new EvidenceStorageProperties("s3", "", "private-evidence", "us-east-1"));
        byte[] content = "evidence".getBytes();

        var stored = storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", "photo.png", "image/png", content));

        assertEquals(content.length, stored.sizeBytes());
        ArgumentCaptor<software.amazon.awssdk.services.s3.model.PutObjectRequest> request =
                ArgumentCaptor.forClass(software.amazon.awssdk.services.s3.model.PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertEquals("private-evidence", request.getValue().bucket());
        assertEquals("image/png", request.getValue().contentType());
        assertTrue(request.getValue().key().endsWith(".png"));
    }
}
