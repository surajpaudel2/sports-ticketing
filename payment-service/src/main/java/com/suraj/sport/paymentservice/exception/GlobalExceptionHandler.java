package com.suraj.sport.paymentservice.exception;

import com.suraj.sport.paymentservice.dto.response.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    @ExceptionHandler(PaymentNotRefundableException.class)
    public ResponseEntity<ApiResult<Void>> handlePaymentNotRefundable(PaymentNotRefundableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    @ExceptionHandler(PaymentNotRetryableException.class)
    public ResponseEntity<ApiResult<Void>> handlePaymentNotRetryable(PaymentNotRetryableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidRefundAmountException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidRefundAmount(InvalidRefundAmountException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.of(false, "An unexpected error occurred", null));
    }
}