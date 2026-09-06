package com.fieldops.evidence.application;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface EvidenceStorage {
    record StoredFile(String storageKey, long sizeBytes, String checksum) {}
    record StoredObject(byte[] content, String contentType) {}

    StoredFile store(UUID inspectionId, MultipartFile file);

    StoredObject read(String storageKey);

    void delete(String storageKey);
}
