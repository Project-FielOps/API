package com.fieldops.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SyncPushRequest(
        @NotNull UUID deviceId,
        @NotNull @Size(min = 1, max = 50) List<@Valid SyncOperationRequest> operations
) {}
