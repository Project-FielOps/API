package com.fieldops.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fieldops.jwt")
public record JwtProperties(String secret, long accessTokenTtlMinutes, long refreshTokenTtlMinutes) {
}
