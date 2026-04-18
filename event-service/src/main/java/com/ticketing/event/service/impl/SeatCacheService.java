package com.ticketing.event.service.impl;

import com.ticketing.event.entity.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Manages the Redis seat-availability cache for events.
 *
 * <p><strong>Key pattern:</strong> {@code event:seats:{eventId}} → integer string
 * representing the current available seat count for that event.</p>
 *
 * <p>Redis is a cache here, not the source of truth — the database is authoritative.
 * The cache exists purely to short-circuit booking requests before they reach the DB
 * lock, reducing contention under concurrent load. A cache miss must always fall back
 * to the database rather than blocking a valid booking.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatCacheService {

    private static final String SEAT_CACHE_KEY_PREFIX = "event:seats:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Returns the cached available seat count for the given event, or empty if
     * no cache entry exists (cold start, Redis restart, or a newly created event).
     *
     * @param eventId the event primary key
     * @return the cached seat count, or {@link Optional#empty()} on a cache miss
     */
    public Optional<Integer> getSeatCount(Long eventId) {
        String cached = redisTemplate.opsForValue().get(buildKey(eventId));
        return Optional.ofNullable(cached).map(Integer::parseInt);
    }

    /**
     * Writes (or overwrites) the available seat count for the given event into Redis.
     *
     * <p>Called after every successful DB mutation so that the cache reflects the
     * post-commit state. Also called on a cache-miss fallback to warm a missing entry.</p>
     *
     * @param eventId the event primary key
     * @param seats   the current available seat count to store
     */
    public void setSeatCount(Long eventId, int seats) {
        redisTemplate.opsForValue().set(buildKey(eventId), String.valueOf(seats));
    }

    /**
     * Warms the seat cache for a batch of events.
     *
     * <p>Called at application startup with all {@code ACTIVE} events so that the very
     * first booking request finds its key in Redis, rather than hitting the DB fallback
     * path. Events created after startup are the responsibility of the event-creation
     * flow to populate individually.</p>
     *
     * @param activeEvents the events whose seat counts should be cached
     */
    public void warmCache(List<Event> activeEvents) {
        for (Event event : activeEvents) {
            setSeatCount(event.getId(), event.getAvailableSeats());
        }
        log.info("Redis seat cache initialised with {} active events", activeEvents.size());
    }

    // ── private ──────────────────────────────────────────────────────────────────

    private String buildKey(Long eventId) {
        return SEAT_CACHE_KEY_PREFIX + eventId;
    }
}