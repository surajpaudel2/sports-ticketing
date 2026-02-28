package com.suraj.sport.bookingservice.exception;

public class BookingNotRebookableException extends RuntimeException {
    public BookingNotRebookableException(String message) {
        super(message);
    }
}