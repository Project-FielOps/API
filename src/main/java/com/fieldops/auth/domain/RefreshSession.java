package com.fieldops.auth.domain;

import com.fieldops.shared.domain.BaseEntity;
import com.fieldops.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_session")
@Getter @Setter @NoArgsConstructor
public class RefreshSession extends BaseEntity {
    @Column(nullable = false, unique = true, length = 80)
    private String jti;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;

    public boolean isUsable(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
