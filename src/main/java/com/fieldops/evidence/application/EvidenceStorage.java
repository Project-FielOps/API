package com.fieldops.evidence.application;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface EvidenceStorage {
    record StoredFile(String storageKey, long sizeBytes, String checksum) {}

    StoredFile store(UUID inspectionId, MultipartFile file);

    void delete(String storageKey);
}
