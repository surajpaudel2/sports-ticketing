package com.suraj.sport.bookingservice.exception;

public class EventNotBookableException extends RuntimeException {
    public EventNotBookableException(String message) { super(message); }
}
