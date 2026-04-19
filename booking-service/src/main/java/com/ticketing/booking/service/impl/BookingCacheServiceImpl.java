package com.ticketing.booking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.booking.dto.cache.BookingCacheDto;
import com.ticketing.booking.service.BookingCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCacheServiceImpl implements BookingCacheService {

    private static final String KEY_PREFIX = "booking:cache:";
    private static final long TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(BookingCacheDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key(dto.bookingId()), json, TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached booking bookingId={} TTL={}min", dto.bookingId(), TTL_MINUTES);
        } catch (JsonProcessingException e) {
            // Non-fatal — listener falls back to DB if cache is missing
            log.error("Failed to cache booking bookingId={}: {}", dto.bookingId(), e.getMessage());
        }
    }

    @Override
    public Optional<BookingCacheDto> get(Long bookingId) {
        String json = redisTemplate.opsForValue().get(key(bookingId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, BookingCacheDto.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached booking bookingId={}: {}", bookingId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void evict(Long bookingId) {
        redisTemplate.delete(key(bookingId));
        log.debug("Evicted cached booking bookingId={}", bookingId);
    }

    private String key(Long bookingId) {
        return KEY_PREFIX + bookingId;
    }
}
