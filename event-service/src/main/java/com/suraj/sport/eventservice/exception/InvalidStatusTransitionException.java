package com.suraj.sport.eventservice.exception;

public class InvalidStatusTransitionException extends RuntimeException {
public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
