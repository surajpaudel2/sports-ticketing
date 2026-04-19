package com.ticketing.booking.service;

import com.ticketing.booking.dto.cache.BookingCacheDto;

import java.util.Optional;

/**
 * Manages the booking snapshot cache in Redis.
 *
 * <p>The cache entry is written once (after PaymentIntent creation) and evicted after the
 * booking reaches its final state (CONFIRMED or FAILED). Its sole purpose is to avoid
 * repeated DB reads in {@code BookingEventListener} during the payment processing window.</p>
 */
public interface BookingCacheService {

    void save(BookingCacheDto dto);

    Optional<BookingCacheDto> get(Long bookingId);

    void evict(Long bookingId);
}
