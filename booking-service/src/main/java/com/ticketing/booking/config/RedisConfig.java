package com.ticketing.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration for Booking Service.
 *
 * <p>Uses {@link StringRedisTemplate} — all values are serialized to JSON strings via
 * {@link com.fasterxml.jackson.databind.ObjectMapper} in {@code BookingCacheServiceImpl}.
 * This keeps Redis data human-readable and avoids Java serialization overhead.</p>
 *
 * <p>Key convention: {@code booking:cache:{bookingId}} → JSON snapshot of the booking,
 * stored with a 30-minute TTL to cover the Stripe payment window.</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
