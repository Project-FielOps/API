package com.fieldops.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fieldops.mail")
public record MailProperties(String provider, String from, String passwordResetUrl, String region) {}
