package com.suraj.sport.bookingservice.exception;

public class BookingNotRetryableException extends RuntimeException {
    public BookingNotRetryableException(String message) {
        super(message);
    }
}