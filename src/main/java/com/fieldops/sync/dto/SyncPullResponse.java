package com.fieldops.sync.dto;

import java.util.List;

public record SyncPullResponse(List<SyncChangeResponse> changes, String nextCursor) {}
