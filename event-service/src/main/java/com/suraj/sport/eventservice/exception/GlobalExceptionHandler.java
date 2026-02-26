package com.suraj.sport.eventservice.exception;

import com.suraj.sport.eventservice.dto.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation ────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body");
    }

    // ── Domain Exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleEventNotFound(EventNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ApiResult<Void>> handleDuplicateEvent(DuplicateEventException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidSeatCountException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidSeatCount(InvalidSeatCountException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EventNotUpdatableException.class)
    public ResponseEntity<ApiResult<Void>> handleEventNotUpdatable(EventNotUpdatableException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── Catch-All ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResult<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiResult.of(false, message, null));
    }
}