package com.fieldops.shared.exception;

/** Thrown for uniqueness violations, optimistic-lock conflicts, or state conflicts (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
