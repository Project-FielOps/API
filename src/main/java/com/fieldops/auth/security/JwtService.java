package com.fieldops.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    /** HS512 exige chave de pelo menos 512 bits — 64 caracteres ASCII. */
    private static final int MIN_SECRET_LENGTH = 64;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(requireStrongSecret(properties.secret()));
    }

    /**
     * Recusa a partida quando a chave de assinatura não foi configurada.
     *
     * <p>Um placeholder não resolvido (`${FIELDOPS_JWT_SECRET}`) chega aqui como
     * texto literal, e uma chave fraca ou previsível permite forjar token para
     * qualquer usuário, inclusive administrador. Falhar na subida, com a causa
     * dita em uma frase, é preferível a subir uma aplicação insegura.
     */
    private static byte[] requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "A variável de ambiente FIELDOPS_JWT_SECRET não está definida. "
                            + "Copie .env.example para .env e gere uma chave com: openssl rand -base64 64");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "FIELDOPS_JWT_SECRET tem " + bytes.length + " bytes; o mínimo para HS512 é "
                            + MIN_SECRET_LENGTH + ". Gere uma nova com: openssl rand -base64 64");
        }
        return bytes;
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
                .id(UUID.randomUUID().toString())
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

    public String extractJti(String token) { return parseClaims(token).getId(); }

    public Instant extractExpiration(String token) { return parseClaims(token).getExpiration().toInstant(); }

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
