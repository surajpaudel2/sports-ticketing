package com.ticketing.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration for Event Service.
 *
 * <p>Uses a {@link StringRedisTemplate} rather than the generic {@code RedisTemplate<Object, Object>}
 * because all seat-availability values stored in Redis are simple integers serialized as strings.
 * This avoids Java serialization overhead and makes the Redis data human-readable for debugging.</p>
 *
 * <p><strong>Redis key convention:</strong> {@code event:seats:{eventId}}</p>
 * <pre>
 *   event:seats:42 → "150"   // 150 seats available for event 42
 * </pre>
 *
 * <p>The cache is populated on application startup by
 * {@code EventServiceImpl#initEventSeatCache} which loads all active events from the
 * database into Redis. Subsequent reads go to Redis first; the database is only consulted
 * when the optimistic-lock confirmation step is reached.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * Provides a {@link StringRedisTemplate} for reading and writing seat counts as strings.
     * Spring Boot auto-configures the underlying {@link RedisConnectionFactory} from
     * {@code spring.data.redis.*} properties in {@code application.yaml}.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
