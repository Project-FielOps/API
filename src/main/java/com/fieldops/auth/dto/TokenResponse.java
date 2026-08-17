package com.fieldops.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    public static TokenResponse of(String accessToken, String refreshToken, long accessTtlMinutes) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTtlMinutes * 60);
    }
}
