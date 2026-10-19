package com.fieldops.auth.application;

import com.fieldops.auth.dto.TokenResponse;
import com.fieldops.auth.dto.ChangePasswordRequest;
import com.fieldops.auth.dto.ResetPasswordRequest;
import com.fieldops.auth.domain.PasswordResetToken;
import com.fieldops.auth.repository.PasswordResetTokenRepository;
import com.fieldops.user.repository.UserRepository;
import com.fieldops.user.domain.User;
import com.fieldops.auth.security.FieldOpsUserDetails;
import com.fieldops.auth.security.JwtProperties;
import com.fieldops.auth.security.JwtService;
import com.fieldops.auth.domain.RefreshSession;
import com.fieldops.auth.repository.RefreshSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.UUID;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MailProperties mailProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Instant> resetRateLimit = new ConcurrentHashMap<>();

    public static final String PASSWORD_RECOVERY_MESSAGE =
            "Se existir uma conta associada ao e-mail informado, as instruções de recuperação serão enviadas.";

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
        FieldOpsUserDetails principal;
        try {
            principal = (FieldOpsUserDetails) userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException ex) {
            // A refresh token must never reveal whether its subject still exists.
            throw new BadCredentialsException("Invalid or expired refresh token", ex);
        }
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
        principal.getUser().setMustChangePassword(false);
        refreshSessionRepository.revokeAllByUserId(principal.getUserId());
    }

    @Transactional
    public void requestPasswordReset(String rawEmail, String clientIp) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String key = clientIp + ":" + email;
        Instant now = Instant.now();
        Instant previous = resetRateLimit.putIfAbsent(key, now.plusSeconds(60));
        if (previous != null && previous.isAfter(now)) return;
        resetRateLimit.put(key, now.plusSeconds(60));
        var userOptional = userRepository.findByEmailIgnoreCase(email);
        log.info("PASSWORD_RESET_REQUESTED userFound={} active={}", userOptional.isPresent(),
                userOptional.map(User::isActive).orElse(false));
        if (userOptional.isEmpty() || !userOptional.get().isActive()) return;
        User user = userOptional.get();
        passwordResetTokenRepository.invalidateActiveByUserId(user.getId());
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(token));
        resetToken.setExpiresAt(now.plusSeconds(1800));
        passwordResetTokenRepository.save(resetToken);
        String separator = mailProperties.passwordResetUrl().contains("?") ? "&" : "?";
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), mailProperties.passwordResetUrl() + separator + "token=" + token);
            log.info("PASSWORD_RESET_EMAIL_SENT userId={}", user.getId());
        } catch (RuntimeException ex) {
            log.error("PASSWORD_RESET_EMAIL_FAILED userId={}", user.getId(), ex);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> {
                    log.warn("PASSWORD_RESET_INVALID_TOKEN");
                    return new BadCredentialsException("Invalid or expired password reset token");
                });
        if (!resetToken.isUsable(Instant.now()) || !resetToken.getUser().isActive()) {
            log.warn("PASSWORD_RESET_EXPIRED_OR_INELIGIBLE userId={}", resetToken.getUser().getId());
            throw new BadCredentialsException("Invalid or expired password reset token");
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        resetToken.setUsedAt(Instant.now());
        refreshSessionRepository.revokeAllByUserId(user.getId());
        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
        log.info("PASSWORD_RESET_COMPLETED userId={}", user.getId());
    }

    private byte[] randomBytes() { byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes); return bytes; }
    private String hashToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    private TokenResponse issueTokens(FieldOpsUserDetails principal) {
        String access = jwtService.generateAccessToken(principal);
        String refresh = jwtService.generateRefreshToken(principal);
        RefreshSession session = new RefreshSession();
        session.setJti(jwtService.extractJti(refresh));
        session.setUser(principal.getUser());
        session.setExpiresAt(jwtService.extractExpiration(refresh));
        refreshSessionRepository.save(session);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenTtlMinutes(), principal.getUser().isMustChangePassword());
    }
}
