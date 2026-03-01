package com.suraj.sport.paymentservice.exception;

public class PaymentNotRetryableException extends RuntimeException {
    public PaymentNotRetryableException(String message) {
        super(message);
    }
}