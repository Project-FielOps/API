package com.fieldops.sync.domain;

public enum SyncOperationStatus {
    APPLIED, ALREADY_APPLIED, REJECTED, CONFLICT, DEPENDENCY_FAILED
}
