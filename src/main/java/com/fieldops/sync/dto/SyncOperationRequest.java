package com.fieldops.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SyncOperationRequest(
        @NotNull UUID operationId,
        @NotNull @Size(max = 50) String type,
        @NotNull UUID entityId,
        Long baseVersion,
        @Size(max = 10) List<UUID> dependsOn,
        @NotNull JsonNode payload,
        @Min(1) @Max(5) Integer attempt
) {
    public int effectiveAttempt() { return attempt == null ? 1 : attempt; }
    public List<UUID> effectiveDependencies() { return dependsOn == null ? List.of() : dependsOn; }
}
