package com.fieldops.sync.dto;

import java.util.List;

public record SyncPushResponse(List<SyncOperationResult> results) {}
