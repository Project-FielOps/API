package com.fieldops.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Standard error response body used by every error path in the API.
 *
 * <pre>
 * {
 *   "timestamp": "2026-08-17T12:00:00Z",
 *   "status": 422,
 *   "error": "Unprocessable Entity",
 *   "code": "REQUIRED_ITEM_MISSING",
 *   "message": "Human readable summary",
 *   "path": "/api/v1/inspections/{id}/submit",
 *   "details": [ {"field": "...", "message": "..."} ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, currentRequestId(), null);
    }

    public static ApiError of(int status, String error, String code, String message, String path, List<FieldError> details) {
        return new ApiError(Instant.now(), status, error, code, message, path, currentRequestId(), details);
    }

    private static String currentRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }
}
