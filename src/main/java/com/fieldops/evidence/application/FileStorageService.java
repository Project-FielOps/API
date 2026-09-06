package com.fieldops.evidence.application;

import com.fieldops.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores evidence files on local disk under a configurable root directory, per spec 10.2's
 * "arquivos fora do banco relacional" principle - the DB keeps only metadata + storage key.
 */
@RequiredArgsConstructor
public class FileStorageService implements EvidenceStorage {

    private final EvidenceStorageProperties properties;

    @Override
    public EvidenceStorage.StoredFile store(UUID inspectionId, MultipartFile file) {
        try {
            Path root = Path.of(properties.evidenceDir()).toAbsolutePath().normalize();
            Path dir = root.resolve(inspectionId.toString());
            Files.createDirectories(dir);
            String extension = extractExtension(file.getOriginalFilename());
            String storageKey = inspectionId + "/" + UUID.randomUUID() + extension;
            Path target = root.resolve(storageKey).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessRuleException("INVALID_FILE", "Invalid file destination");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String checksum = checksum(target);
            return new EvidenceStorage.StoredFile(storageKey, Files.size(target), checksum);
        } catch (IOException e) {
            throw new BusinessRuleException("FILE_STORAGE_ERROR", "Could not store the uploaded file: " + e.getMessage());
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path root = Path.of(properties.evidenceDir()).toAbsolutePath().normalize();
            Files.deleteIfExists(root.resolve(storageKey).normalize());
        } catch (IOException e) {
            // best-effort: metadata deletion/blocking is what the spec requires; ignore disk errors here
        }
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null) return "";
        int idx = originalFileName.lastIndexOf('.');
        return idx >= 0 ? originalFileName.substring(idx) : "";
    }

    private String checksum(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
