package com.fieldops.evidence.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fieldops.storage")
public record EvidenceStorageProperties(String evidenceDir) {
}
