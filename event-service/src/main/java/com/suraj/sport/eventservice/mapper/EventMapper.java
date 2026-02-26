package com.suraj.sport.eventservice.mapper;

import com.suraj.sport.eventservice.dto.request.CreateEventRequest;
import com.suraj.sport.eventservice.dto.request.UpdateEventRequest;
import com.suraj.sport.eventservice.dto.response.CreateEventResponse;
import com.suraj.sport.eventservice.dto.response.EventResponse;
import com.suraj.sport.eventservice.dto.response.UpdateEventResponse;
import com.suraj.sport.eventservice.entity.Event;
import com.suraj.sport.eventservice.entity.EventStatus;

public class EventMapper {

    private EventMapper() {
    }

    public static Event mapToEvent(CreateEventRequest createEventRequest) {
        return Event.builder()
                .name(createEventRequest.getName())
                .sportType(createEventRequest.getSportType())
                .venue(createEventRequest.getVenue())
                .eventDate(createEventRequest.getEventDate())
                .totalSeats(createEventRequest.getTotalSeats())
                .availableSeats(createEventRequest.getTotalSeats())
                .pricePerSeat(createEventRequest.getPricePerSeat())
                .status(EventStatus.UPCOMING)
                .build();
    }

    public static CreateEventResponse mapToCreateEventResponse(Event event) {
        return new CreateEventResponse(
                event.getId(),
                event.getName(),
                event.getCreatedAt()
        );
    }

    public static Event mapToUpdatedEvent(UpdateEventRequest request, Event existingEvent) {
        existingEvent.setName(request.getName());
        existingEvent.setSportType(request.getSportType());
        existingEvent.setVenue(request.getVenue());
        existingEvent.setEventDate(request.getEventDate());
        existingEvent.setTotalSeats(request.getTotalSeats());
        existingEvent.setPricePerSeat(request.getPricePerSeat());
        existingEvent.setStatus(request.getStatus());
        return existingEvent;
    }

    public static UpdateEventResponse mapToUpdateEventResponse(Event event) {
        return new UpdateEventResponse(
                event.getId(),
                event.getName(),
                event.getSportType(),
                event.getVenue(),
                event.getEventDate(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getPricePerSeat(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }

    public static EventResponse mapToEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getSportType(),
                event.getVenue(),
                event.getEventDate(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getPricePerSeat(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
