package com.fieldops.shared.exception;

/** Thrown when an authenticated user is not allowed to perform this specific action (HTTP 403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
