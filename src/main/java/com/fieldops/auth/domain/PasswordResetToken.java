package com.fieldops.auth.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "password_reset_token")
@Getter @Setter @NoArgsConstructor
public class PasswordResetToken extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    public boolean isUsable(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
}
