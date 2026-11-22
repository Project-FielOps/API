package com.fieldops.auth.repository;

import com.fieldops.auth.domain.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByJti(String jti);
    @Modifying
    @Query("update RefreshSession s set s.revokedAt = CURRENT_TIMESTAMP where s.jti = :jti and s.revokedAt is null")
    int revokeByJti(String jti);
    @Modifying
    @Query("update RefreshSession s set s.revokedAt = CURRENT_TIMESTAMP where s.user.id = :userId and s.revokedAt is null")
    int revokeAllByUserId(UUID userId);
}
