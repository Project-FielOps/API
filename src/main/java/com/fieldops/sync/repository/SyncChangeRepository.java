package com.fieldops.sync.repository;

import com.fieldops.sync.domain.SyncChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SyncChangeRepository extends JpaRepository<SyncChange, Long> {
    List<SyncChange> findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(UUID userId, Long sequence, Pageable pageable);
}
