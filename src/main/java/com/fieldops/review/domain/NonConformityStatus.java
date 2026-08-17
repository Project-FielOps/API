package com.fieldops.review.domain;

/** MVP only ever creates OPEN (spec 10.10); the remaining values exist so status can evolve post-MVP. */
public enum NonConformityStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
