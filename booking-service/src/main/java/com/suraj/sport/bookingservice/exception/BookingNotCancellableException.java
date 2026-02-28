package com.suraj.sport.bookingservice.exception;

public class BookingNotCancellableException extends RuntimeException {
    public BookingNotCancellableException(String message) {
        super(message);
    }
}
