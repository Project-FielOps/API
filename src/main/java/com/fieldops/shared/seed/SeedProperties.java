package com.fieldops.shared.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fieldops.seed")
public record SeedProperties(boolean enabled) {
}
