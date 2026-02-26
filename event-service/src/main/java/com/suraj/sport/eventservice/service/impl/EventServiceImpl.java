package com.suraj.sport.eventservice.service.impl;

import com.suraj.sport.eventservice.dto.request.CreateEventRequest;
import com.suraj.sport.eventservice.dto.request.UpdateEventRequest;
import com.suraj.sport.eventservice.dto.response.CreateEventResponse;
import com.suraj.sport.eventservice.dto.response.EventResponse;
import com.suraj.sport.eventservice.dto.response.UpdateEventResponse;
import com.suraj.sport.eventservice.entity.Event;
import com.suraj.sport.eventservice.entity.EventStatus;
import com.suraj.sport.eventservice.exception.*;
import com.suraj.sport.eventservice.mapper.EventMapper;
import com.suraj.sport.eventservice.repository.EventRepository;
import com.suraj.sport.eventservice.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    // =====================================================================
    // CREATE EVENT
    // =====================================================================

    /**
     * Creates a new sports event.
     * - Status is automatically set to UPCOMING on creation.
     * - Available seats are automatically set equal to total seats on creation.
     * - Duplicate events (same name, venue, and date) are rejected.
     */
    @Override
    public CreateEventResponse createEvent(CreateEventRequest createEventRequest) {

        // Edge case: reject duplicate events to prevent accidental double-creation
        // by organizers or repeated API calls
        eventRepository.findByNameAndVenueAndEventDate(
                createEventRequest.getName(),
                createEventRequest.getVenue(),
                createEventRequest.getEventDate()
        ).ifPresent(e -> {
            throw new DuplicateEventException("An event with the same name, venue and date already exists");
        });

        // Map request to entity — status defaults to UPCOMING, availableSeats = totalSeats
        Event event = EventMapper.mapToEvent(createEventRequest);
        Event savedEvent = eventRepository.save(event);

        // TODO: validateSportType(createEventRequest.getSportType())
        // Integrate with an AI model or sports reference API (e.g. SportsDB) to verify
        // that the provided sport type is real and supported.
        // e.g. "Cricket", "Football" valid — random strings should be rejected.

        // TODO: validateAndEnrichVenue(createEventRequest.getVenue())
        // Integrate with Google Maps or a venue API to verify the venue exists.
        // Can also enrich the event with real stadium data — capacity, GPS coordinates,
        // sections, seat numbers etc.

        // TODO: notifyOrganizer(savedEvent)
        // Once event is created, send a confirmation email/SMS to the organizer
        // with event details and a management link.

        return EventMapper.mapToCreateEventResponse(savedEvent);
    }

    // =====================================================================
    // UPDATE EVENT
    // =====================================================================

    /**
     * Updates an existing sports event.
     *
     * Allowed status transitions:
     *   UPCOMING  -> UPCOMING or ONGOING
     *   ONGOING   -> ONGOING, COMPLETED or CANCELLED
     *
     * Restrictions:
     *   - CANCELLED and COMPLETED events are immutable — cannot be updated.
     *   - Event date cannot be changed once the event is ONGOING.
     *   - Available seats are recalculated automatically if total seats change.
     *   - Available seats cannot be manually set — managed by the booking system.
     */
    @Override
    public UpdateEventResponse updateEvent(Long eventId, UpdateEventRequest updateEventRequest) {

        Event event = findEventOrThrow(eventId);

        validateEventIsUpdatable(event);
        validateStatusTransition(event.getStatus(), updateEventRequest.getStatus());
        validateEventDateChange(event, updateEventRequest);
        recalculateAvailableSeatsIfNeeded(event, updateEventRequest.getTotalSeats());

        // TODO: notifyUsersIfVenueChanged(event, updateEventRequest)
        // If venue has changed and bookings exist, notify all booked users via
        // email/SMS with the new venue details.

        // TODO: notifyUsersIfDateChanged(event, updateEventRequest)
        // If event date has changed and bookings exist, notify all booked users.
        // Users should be given the option to keep their booking or request a refund.

        // FIXME: handleCancellationWithBookings(event)
        // If an event is being CANCELLED and active bookings exist:
        // 1. Fetch all bookings from Booking Service
        // 2. Trigger refund process via Payment Service for each booking
        // 3. Notify all affected users via Notification Service
        // This requires inter-service communication — revisit when Kafka is implemented.

        // FIXME: handleDateChangeWithBookings(event, updateEventRequest)
        // If event date changes after bookings are made:
        // Option A: Auto-refund all existing bookings
        // Option B: Notify users and allow them to either keep booking or request refund
        // Option C: Allow users to re-select seats for new date with same payment
        // Business decision needed — revisit when Notification + Payment services are wired.

        Event updatedEvent = EventMapper.mapToUpdatedEvent(updateEventRequest, event);
        Event savedEvent = eventRepository.save(updatedEvent);

        return EventMapper.mapToUpdateEventResponse(savedEvent);
    }

    // =====================================================================
// GET EVENT BY ID
// =====================================================================

    /**
     * Retrieves a sports event by its unique ID.
     *
     * Restrictions:
     *   - Throws EventNotFoundException if no event exists with the given ID.
     *
     * TODO: In the future, consider caching frequently accessed events
     * using Redis to reduce database hits — especially useful for high-traffic
     * events like IPL finals or World Cup matches.
     */
    @Override
    public EventResponse getEventById(Long eventId) {

        // Edge case: event must exist
        Event event = findEventOrThrow(eventId);

        return EventMapper.mapToEventResponse(event);
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    /**
     * Fetches the event by ID or throws EventNotFoundException if not found.
     */
    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Ensures the event is in a state that allows updates.
     * CANCELLED and COMPLETED events are immutable — no further changes allowed.
     */
    private void validateEventIsUpdatable(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new EventNotUpdatableException("Cannot update an event that is " + event.getStatus());
        }
    }

    /**
     * Validates that the requested status transition is allowed.
     *
     * Allowed transitions:
     *   UPCOMING -> UPCOMING or ONGOING
     *   ONGOING  -> ONGOING, COMPLETED or CANCELLED
     *
     * Note: Direct jump from UPCOMING to COMPLETED is intentionally blocked.
     * Status must progress naturally through the lifecycle.
     */
    private void validateStatusTransition(EventStatus currentStatus, EventStatus requestedStatus) {
        if (currentStatus == EventStatus.UPCOMING &&
                requestedStatus != EventStatus.UPCOMING &&
                requestedStatus != EventStatus.ONGOING) {
            throw new InvalidStatusTransitionException(
                    "UPCOMING event can only transition to UPCOMING or ONGOING");
        }
        if (currentStatus == EventStatus.ONGOING &&
                requestedStatus != EventStatus.ONGOING &&
                requestedStatus != EventStatus.COMPLETED &&
                requestedStatus != EventStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(
                    "ONGOING event can only transition to ONGOING, COMPLETED or CANCELLED");
        }
    }

    /**
     * Prevents event date changes when the event is already ONGOING.
     * Once an event has started, its schedule is locked.
     *
     * Note: Date changes for UPCOMING events are allowed but should trigger
     * user notifications if bookings already exist (see TODO above).
     */
    private void validateEventDateChange(Event event, UpdateEventRequest request) {
        if (event.getStatus() == EventStatus.ONGOING &&
                !request.getEventDate().equals(event.getEventDate())) {
            throw new InvalidEventDateException(
                    "Cannot change event date when event is ONGOING");
        }
    }

    /**
     * Recalculates available seats when total seats are updated.
     * Formula: availableSeats = newTotalSeats - bookedSeats
     *
     * Throws InvalidSeatCountException if the new total is less than already booked seats.
     *
     * NOTE: In the future, if total seats are reduced below booked seats,
     * consider a partial cancellation strategy — e.g. refund the most recently
     * booked users first (LIFO) or allow organizer to choose which bookings to cancel.
     */
    private void recalculateAvailableSeatsIfNeeded(Event event, int newTotalSeats) {
        if (newTotalSeats != event.getTotalSeats()) {
            int bookedSeats = event.getTotalSeats() - event.getAvailableSeats();
            int newAvailableSeats = newTotalSeats - bookedSeats;
            if (newAvailableSeats < 0) {
                throw new InvalidSeatCountException(
                        "Total seats cannot be less than already booked seats: " + bookedSeats);
            }
            event.setAvailableSeats(newAvailableSeats);
        }
    }
}