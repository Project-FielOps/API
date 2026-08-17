package com.fieldops.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USER_ID = "uid";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes());
    }

    public String generateAccessToken(FieldOpsUserDetails principal) {
        return buildToken(principal, TYPE_ACCESS, properties.accessTokenTtlMinutes());
    }

    public String generateRefreshToken(FieldOpsUserDetails principal) {
        return buildToken(principal, TYPE_REFRESH, properties.refreshTokenTtlMinutes());
    }

    private String buildToken(FieldOpsUserDetails principal, String type, long ttlMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claims(Map.of(CLAIM_ROLE, principal.getUser().getRole().name(), CLAIM_TYPE, type,
                        CLAIM_USER_ID, principal.getUserId().toString()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_USER_ID, String.class));
    }

    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(Date.from(Instant.now()));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
