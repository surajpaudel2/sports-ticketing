package com.suraj.sport.eventservice.service;

import com.suraj.sport.eventservice.dto.request.CreateEventRequest;
import com.suraj.sport.eventservice.dto.request.UpdateEventRequest;
import com.suraj.sport.eventservice.dto.response.CreateEventResponse;
import com.suraj.sport.eventservice.dto.response.EventResponse;
import com.suraj.sport.eventservice.dto.response.UpdateEventResponse;

import java.util.List;

public interface EventService {

    CreateEventResponse createEvent(CreateEventRequest createEventRequest);

    UpdateEventResponse updateEvent(Long eventId, UpdateEventRequest updateEventRequest);

    EventResponse getEventById(Long eventId);

    List<EventResponse> getAllEvents();
}
