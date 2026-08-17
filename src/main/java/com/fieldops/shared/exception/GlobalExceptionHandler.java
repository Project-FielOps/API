package com.fieldops.shared.exception;

import com.fieldops.shared.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), message(fe)))
                .toList();
        ApiError body = ApiError.of(400, "Bad Request", "VALIDATION_ERROR",
                "One or more fields are invalid", req.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private String message(FieldError fe) {
        return fe.getDefaultMessage() == null ? "invalid value" : fe.getDefaultMessage();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(400, "Bad Request", "MALFORMED_REQUEST_BODY",
                "Request body could not be parsed", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(400, "Bad Request", "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(400, "Bad Request", "INVALID_REQUEST",
                ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(401, "Unauthorized", "INVALID_CREDENTIALS",
                "Invalid email or password", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(401, "Unauthorized", "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler({AccessDeniedException.class, ForbiddenOperationException.class})
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest req) {
        ApiError body = ApiError.of(403, "Forbidden", "ACCESS_DENIED",
                ex.getMessage() != null ? ex.getMessage() : "You do not have permission to perform this action",
                req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(404, "Not Found", "NOT_FOUND", ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(422, "Unprocessable Entity", ex.getRuleCode(), ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(422).body(body);
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest req) {
        String message = ex instanceof ObjectOptimisticLockingFailureException
                ? "The resource was modified by another request. Please reload and try again."
                : (ex instanceof ConflictException ? ex.getMessage() : "The request conflicts with the current state of the resource");
        ApiError body = ApiError.of(409, "Conflict", "CONFLICT", message, req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(413, "Payload Too Large", "FILE_TOO_LARGE",
                "Uploaded file exceeds the maximum allowed size", req.getRequestURI());
        return ResponseEntity.status(413).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        ApiError body = ApiError.of(415, "Unsupported Media Type", "UNSUPPORTED_MEDIA_TYPE",
                "The provided content type is not supported", req.getRequestURI());
        return ResponseEntity.status(415).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error handling {} {}", req.getMethod(), req.getRequestURI(), ex);
        ApiError body = ApiError.of(500, "Internal Server Error", "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
