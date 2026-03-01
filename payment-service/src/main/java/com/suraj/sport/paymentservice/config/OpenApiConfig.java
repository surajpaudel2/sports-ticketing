package com.suraj.sport.paymentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sports Ticketing - Payment Service")
                        .description("Manages payment processing, transaction tracking and refunds for sports event bookings")
                        .version("1.0.0"));
    }
}