package com.fieldops.evidence.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class EvidenceStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "fieldops.storage.mode", havingValue = "s3")
    S3Client s3Client(EvidenceStorageProperties properties) {
        return S3Client.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "fieldops.storage.mode", havingValue = "local", matchIfMissing = true)
    EvidenceStorage localEvidenceStorage(EvidenceStorageProperties properties) {
        return new FileStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "fieldops.storage.mode", havingValue = "s3")
    EvidenceStorage s3EvidenceStorage(S3Client s3Client, EvidenceStorageProperties properties) {
        return new S3EvidenceStorageService(s3Client, properties);
    }
}
