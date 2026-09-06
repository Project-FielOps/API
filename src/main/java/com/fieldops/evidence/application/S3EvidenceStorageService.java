package com.fieldops.evidence.application;

import com.fieldops.shared.exception.BusinessRuleException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public class S3EvidenceStorageService implements EvidenceStorage {

    private final S3Client s3Client;
    private final EvidenceStorageProperties properties;

    public S3EvidenceStorageService(S3Client s3Client, EvidenceStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
        if (properties.bucket() == null || properties.bucket().isBlank()
                || properties.region() == null || properties.region().isBlank()) {
            throw new IllegalStateException("S3 storage requires FIELDOPS_STORAGE_BUCKET and AWS_REGION");
        }
    }

    @Override
    public StoredFile store(UUID inspectionId, MultipartFile file) {
        try {
            String extension = extractExtension(file.getOriginalFilename());
            String storageKey = inspectionId + "/" + UUID.randomUUID() + extension;
            byte[] content = file.getBytes();
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(storageKey)
                            .contentType(normalizeContentType(file.getContentType()))
                            .build(), RequestBody.fromBytes(content));
            return new StoredFile(storageKey, content.length, checksum(content));
        } catch (IOException e) {
            throw new BusinessRuleException("FILE_STORAGE_ERROR", "Could not read the uploaded file: " + e.getMessage());
        }
    }

    @Override
    public StoredObject read(String storageKey) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket()).key(storageKey).build());
            return new StoredObject(response.readAllBytes(), response.response().contentType());
        } catch (IOException e) {
            throw new BusinessRuleException("FILE_STORAGE_ERROR", "Could not read the stored evidence");
        } catch (RuntimeException e) {
            throw new BusinessRuleException("FILE_NOT_FOUND", "Evidence content is no longer available");
        }
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(builder -> builder.bucket(properties.bucket()).key(storageKey));
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null) return "";
        int idx = originalFileName.lastIndexOf('.');
        return idx >= 0 ? originalFileName.substring(idx) : "";
    }

    private String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private String normalizeContentType(String contentType) {
        return "image/jpg".equalsIgnoreCase(contentType) ? "image/jpeg" : contentType;
    }
}
