package com.ticketing.event.service.impl;

import com.ticketing.event.entity.Event;
import com.ticketing.event.service.SeatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatCacheServiceImpl implements SeatCacheService {

    private static final String SEAT_CACHE_KEY_PREFIX = "event:seats:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<Integer> getSeatCount(Long eventId) {
        String cached = redisTemplate.opsForValue().get(buildKey(eventId));
        return Optional.ofNullable(cached).map(Integer::parseInt);
    }

    @Override
    public void setSeatCount(Long eventId, int seats) {
        redisTemplate.opsForValue().set(buildKey(eventId), String.valueOf(seats));
    }

    @Override
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
