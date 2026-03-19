package com.suraj.sport.notificationservice.demo.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "booking-dto2")
@Getter @Setter
public class BookingDto2 {

    private String field1;
    private int field2;
    private boolean field3;

}
