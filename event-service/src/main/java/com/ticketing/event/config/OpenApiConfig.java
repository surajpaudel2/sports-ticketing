package com.ticketing.event.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for Event Service.
 * Swagger UI is available at {@code http://localhost:8083/swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Service API")
                        .description("""
                                Manages sporting event inventory and seat reservations.

                                Key operations:
                                - checkAndReserve: validates seat availability in Redis, confirms under
                                  optimistic locking in the DB, and atomically deducts the reserved seats.
                                - releaseSeats: compensating operation that restores seats when a payment
                                  fails after a prior checkAndReserve succeeded.
                                """)
                        .version("1.0.0"));
    }
}
