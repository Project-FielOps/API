package com.fieldops.auth.repository;

import com.fieldops.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = CURRENT_TIMESTAMP where t.user.id = :userId and t.usedAt is null and t.expiresAt > CURRENT_TIMESTAMP")
    int invalidateActiveByUserId(@Param("userId") UUID userId);
}
