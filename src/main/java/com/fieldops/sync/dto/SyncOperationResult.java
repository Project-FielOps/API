package com.fieldops.sync.dto;

import com.fieldops.sync.domain.SyncOperationStatus;

import java.util.UUID;

public record SyncOperationResult(UUID operationId, SyncOperationStatus status, String code, Object payload) {}
