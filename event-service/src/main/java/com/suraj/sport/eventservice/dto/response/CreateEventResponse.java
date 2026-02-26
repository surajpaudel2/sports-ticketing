package com.suraj.sport.eventservice.dto.response;


import java.time.LocalDateTime;

public record CreateEventResponse(long id, String name, LocalDateTime createdAt) {

}