package com.ticketing.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Payment Service.
 *
 * <p>{@link EnableScheduling} activates Spring's scheduling infrastructure, which is
 * required by {@link com.ticketing.payment.outbox.OutboxEventPoller} to poll the outbox
 * table on a fixed interval and deliver pending events to RabbitMQ.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
