package com.fieldops.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncChangeResponse(long sequence, String entityType, UUID entityId, UUID operationId,
                                 Object payload, Instant createdAt) {}
