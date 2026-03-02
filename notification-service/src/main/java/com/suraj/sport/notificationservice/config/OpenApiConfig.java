package com.suraj.sport.notificationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sports Ticketing - Notification Service")
                        .description("Manages sending and tracking of notifications across all services. " +
                                "Handles email notifications for booking confirmations, cancellations, " +
                                "payment success/failure, and refund updates. " +
                                "Uses Thymeleaf templates for rich HTML emails and sends asynchronously.")
                        .version("1.0.0"));
    }
}