package com.suraj.sport.eventservice.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(Long id) {
        super("Event record not found for ID: " + id);
    }
}