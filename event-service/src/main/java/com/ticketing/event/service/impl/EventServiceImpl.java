package com.ticketing.event.service.impl;

import com.ticketing.event.dto.response.CheckAndReserveResponse;
import com.ticketing.event.entity.Event;
import com.ticketing.event.entity.EventStatus;
import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.exception.InsufficientSeatsException;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.service.EventService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements seat-reservation and seat-release operations for sporting events.
 *
 * <p><strong>checkAndReserve concurrency model (pessimistic locking):</strong></p>
 * <pre>
 *   Thread A (booking req 1)            Thread B (booking req 2)
 *   Redis check: 10 seats ✓             Redis check: 10 seats ✓
 *   SELECT … FOR UPDATE → lock acquired  SELECT … FOR UPDATE → BLOCKS (waits at DB)
 *   seats check ✓, deduct 2, commit      lock acquired — now reads seats=8
 *   lock released                        seats check ✓/✗, deduct or throw
 * </pre>
 *
 * <p>Thread B is serialized at the database level — it cannot even read the row
 * until Thread A's transaction commits. This prevents any possibility of two threads
 * simultaneously reading the same seat count and both deducting from it. The Redis
 * pre-check reduces the number of requests that reach the DB lock at all, keeping
 * contention (and thus wait time) low under concurrent load.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final SeatCacheService seatCacheService;

    /**
     * Warms the Redis seat-availability cache on application startup.
     *
     * <p>Loads all {@code ACTIVE} events from the database and delegates to
     * {@link SeatCacheService#warmCache} to populate Redis. This ensures that
     * {@code checkAndReserve} finds keys in Redis immediately on the first booking
     * request, rather than treating every event as a cache miss until first access.</p>
     *
     * <p>Events added after startup must populate Redis individually (e.g. via the
     * event-create endpoint calling {@link SeatCacheService#setSeatCount}).</p>
     */
    @PostConstruct
    public void initEventSeatCache() {
        List<Event> activeEvents = eventRepository.findByStatus(EventStatus.ACTIVE);
        seatCacheService.warmCache(activeEvents);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation detail — two-phase check:</strong></p>
     * <ol>
     *   <li>Read {@code event:seats:{eventId}} from Redis via {@link #getCachedSeatCountWithFallback}.
     *       Absent key → falls back to DB and re-warms the cache entry.
     *       Value &lt; {@code seats} → {@link InsufficientSeatsException}.</li>
     *   <li>Load the event from the DB via {@code SELECT … FOR UPDATE} (pessimistic write lock).
     *       The lock is held for the duration of the {@code @Transactional} method, preventing
     *       any concurrent transaction from reading or modifying the same row.
     *       Re-check {@code availableSeats} — the authoritative value now that the lock is held.
     *       Value &lt; {@code seats} → {@link InsufficientSeatsException} (lock released on throw).</li>
     *   <li>Deduct {@code seats} from {@code availableSeats} and save. No version check needed —
     *       the exclusive lock guarantees no other transaction can have modified the row.</li>
     *   <li>Update Redis after save — the lock is released when the transaction commits.</li>
     * </ol>
     */
    @Override
    @Transactional
    public CheckAndReserveResponse checkAndReserve(Long eventId, int seats) {

        // ── Step 1: Redis pre-check ──────────────────────────────────────────────────
        int cachedSeats = getCachedSeatCountWithFallback(eventId);
        validateSufficientSeats(cachedSeats, seats, "Redis cache");

        log.debug("Redis pre-check passed: eventId={} requested={} cached={}",
                eventId, seats, cachedSeats);

        // ── Steps 2 & 3: DB confirmation under pessimistic lock ──────────────────────
        Event event = deductSeatsInDb(eventId, seats);

        // ── Step 4: Update Redis to reflect the post-commit DB state ─────────────────
        seatCacheService.setSeatCount(eventId, event.getAvailableSeats());

        log.info("Seats reserved: eventId={} reserved={} remaining={}",
                eventId, seats, event.getAvailableSeats());

        return new CheckAndReserveResponse(event.getId(), event.getName(), event.getPricePerSeat(), seats);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Increments {@code availableSeats} in the DB and updates Redis to match.
     * Rolls back the event status from {@code SOLD_OUT} to {@code ACTIVE} if the
     * release brings {@code availableSeats} above zero.</p>
     */
    @Override
    @Transactional
    public void releaseSeats(Long eventId, int seats) {

        // Also lock here — prevents a concurrent checkAndReserve from reading
        // a seat count that is mid-release (e.g. SOLD_OUT being restored to ACTIVE)
        int updatedSeats = restoreSeatsInDb(eventId, seats);

        // Update Redis to reflect the restored seat count
        seatCacheService.setSeatCount(eventId, updatedSeats);

        log.info("Seats released: eventId={} released={} remaining={}", eventId, seats, updatedSeats);
    }

    // ── private helpers ───────────────────────────────────────────────────────────

    /**
     * Returns the cached available seat count for the given event.
     *
     * <p>On a cache miss (cold start, Redis restart, or a new event created after startup),
     * falls back to the DB to verify the event is active, re-warms the cache entry, and
     * returns the DB value so the calling flow can continue without interruption.</p>
     *
     * @param eventId the event primary key
     * @return current available seat count from Redis (or DB on miss)
     * @throws EventNotFoundException if no active event exists with the given ID
     */
    private int getCachedSeatCountWithFallback(Long eventId) {
        return seatCacheService.getSeatCount(eventId)
                .orElseGet(() -> {
                    // Redis miss — Redis is a cache, not the source of truth.
                    // Fall back to the DB so a cold start, Redis restart, or a new event
                    // that was created after startup does not incorrectly block bookings.
                    log.warn("Redis cache miss for eventId={} — falling back to DB", eventId);

                    Event dbEvent = eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)
                            .orElseThrow(() -> {
                                log.warn("Event not found or not active: eventId={}", eventId);
                                return new EventNotFoundException("Event not found: " + eventId);
                            });

                    // Event is active in DB — warm the cache entry and continue using the DB value.
                    // Subsequent requests will hit Redis normally.
                    seatCacheService.setSeatCount(eventId, dbEvent.getAvailableSeats());
                    log.info("Redis cache warmed from DB fallback: eventId={} availableSeats={}",
                            eventId, dbEvent.getAvailableSeats());

                    return dbEvent.getAvailableSeats();
                });
    }

    /**
     * Throws {@link InsufficientSeatsException} if {@code available} is less than {@code requested}.
     *
     * @param available  current seat count (from Redis or DB)
     * @param requested  seats the caller wants to reserve
     * @param source     label used in the log message to identify where the count came from
     */
    private void validateSufficientSeats(int available, int requested, String source) {
        if (available < requested) {
            log.warn("Insufficient seats in {}: requested={} available={}", source, requested, available);
            throw new InsufficientSeatsException(
                    "Insufficient seats available — requested " + requested + " but only " + available + " remain");
        }
    }

    /**
     * Acquires a pessimistic DB lock on the event row, re-validates seat availability,
     * deducts the requested seats, transitions the event to {@code SOLD_OUT} if it
     * reaches zero, and persists the result.
     *
     * <p>The exclusive lock is held for the lifetime of the enclosing {@code @Transactional}
     * method, so no concurrent transaction can read or modify this row until the transaction
     * commits — eliminating lost-update races without needing a version column.</p>
     *
     * @param eventId the event primary key
     * @param seats   number of seats to deduct
     * @return the saved {@link Event} with the updated {@code availableSeats}
     * @throws EventNotFoundException      if the event does not exist in the DB
     * @throws InsufficientSeatsException  if the DB seat count is below {@code seats}
     *                                     (can happen when the Redis cache was stale)
     */
    private Event deductSeatsInDb(Long eventId, int seats) {
        // SELECT … FOR UPDATE acquires an exclusive row lock for the duration of this
        // @Transactional method. Concurrent transactions block at the DB level until
        // this transaction commits — no stale reads, no lost updates possible.
        Event event = eventRepository.findByIdWithLock(eventId)
                .orElseThrow(() -> {
                    log.error("Event found in Redis but not in DB — data inconsistency: eventId={}", eventId);
                    return new EventNotFoundException("Event not found: " + eventId);
                });

        // Re-check under lock — the Redis pre-check may have been based on a slightly
        // stale value; the DB value here is authoritative
        validateSufficientSeats(event.getAvailableSeats(), seats, "DB");

        int updatedSeats = event.getAvailableSeats() - seats;
        event.setAvailableSeats(updatedSeats);

        // Automatically transition to SOLD_OUT when the last seats are reserved
        if (updatedSeats == 0) {
            event.setStatus(EventStatus.SOLD_OUT);
            log.info("Event sold out: eventId={}", eventId);
        }

        return eventRepository.save(event);
    }

    /**
     * Acquires a pessimistic DB lock on the event row, adds the released seats back,
     * restores the status to {@code ACTIVE} if the event was {@code SOLD_OUT}, and
     * persists the result.
     *
     * @param eventId the event primary key
     * @param seats   number of seats to release back into inventory
     * @return the updated available seat count after the release
     * @throws EventNotFoundException if no event with the given ID exists
     */
    private int restoreSeatsInDb(Long eventId, int seats) {
        Event event = eventRepository.findByIdWithLock(eventId)
                .orElseThrow(() -> {
                    log.error("Cannot release seats — event not found: eventId={}", eventId);
                    return new EventNotFoundException("Event not found: " + eventId);
                });

        int updatedSeats = event.getAvailableSeats() + seats;
        event.setAvailableSeats(updatedSeats);

        // Restore to ACTIVE if the event was SOLD_OUT and seats are now available again
        if (event.getStatus() == EventStatus.SOLD_OUT && updatedSeats > 0) {
            event.setStatus(EventStatus.ACTIVE);
            log.info("Event restored to ACTIVE after seat release: eventId={}", eventId);
        }

        eventRepository.save(event);
        return updatedSeats;
    }
}