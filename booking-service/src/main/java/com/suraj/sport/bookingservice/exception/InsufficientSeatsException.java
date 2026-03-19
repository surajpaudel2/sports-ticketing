package com.suraj.sport.bookingservice.exception;

public class InsufficientSeatsException extends RuntimeException {
    public InsufficientSeatsException(String message) { super(message); }
}