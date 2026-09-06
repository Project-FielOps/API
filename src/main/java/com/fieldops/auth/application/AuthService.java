package com.fieldops.auth.application;

import com.fieldops.auth.dto.TokenResponse;
import com.fieldops.auth.dto.ChangePasswordRequest;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.auth.security.JwtProperties;
import com.fieldops.auth.security.JwtService;
import com.fieldops.auth.domain.RefreshSession;
import com.fieldops.auth.repository.RefreshSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse login(String email, String password) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            FieldOpsUserDetails principal = (FieldOpsUserDetails) authentication.getPrincipal();
            return issueTokens(principal);
        } catch (DisabledException | LockedException e) {
            throw new BadCredentialsException("Account is not active");
        }
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtService.isValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        String email = jwtService.extractEmail(refreshToken);
        FieldOpsUserDetails principal = (FieldOpsUserDetails) userDetailsService.loadUserByUsername(email);
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new BadCredentialsException("Account is not active");
        }
        String jti = jwtService.extractJti(refreshToken);
        RefreshSession session = refreshSessionRepository.findByJti(jti)
                .orElseThrow(() -> new BadCredentialsException("Invalid or revoked refresh token"));
        if (!session.isUsable(Instant.now()) || !session.getUser().getId().equals(principal.getUserId())) {
            throw new BadCredentialsException("Invalid or revoked refresh token");
        }
        session.setRevokedAt(Instant.now());
        return issueTokens(principal);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && jwtService.isValid(refreshToken) && jwtService.isRefreshToken(refreshToken)) {
            refreshSessionRepository.revokeByJti(jwtService.extractJti(refreshToken));
        }
    }

    @Transactional
    public void invalidateAllSessions(UUID userId) { refreshSessionRepository.revokeAllByUserId(userId); }

    @Transactional
    public void changePassword(FieldOpsUserDetails principal, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), principal.getUser().getPasswordHash())) {
            throw new BadCredentialsException("Invalid current password");
        }
        principal.getUser().setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshSessionRepository.revokeAllByUserId(principal.getUserId());
    }

    private TokenResponse issueTokens(FieldOpsUserDetails principal) {
        String access = jwtService.generateAccessToken(principal);
        String refresh = jwtService.generateRefreshToken(principal);
        RefreshSession session = new RefreshSession();
        session.setJti(jwtService.extractJti(refresh));
        session.setUser(principal.getUser());
        session.setExpiresAt(jwtService.extractExpiration(refresh));
        refreshSessionRepository.save(session);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenTtlMinutes());
    }
}
