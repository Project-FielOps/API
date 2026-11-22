package com.fieldops.sync.repository;

import com.fieldops.sync.domain.SyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, UUID> {
    Optional<SyncOperation> findByUserIdAndDeviceIdAndOperationId(UUID userId, UUID deviceId, UUID operationId);

    int deleteByProcessedAtBefore(Instant cutoff);
}
