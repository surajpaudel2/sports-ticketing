package com.suraj.sport.notificationservice.demo.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "booking")
@Getter @Setter
public class BookingDto {

    private String message;
    private String name;
    private Map<String, String> address;
    private List<String> onCallSupport;
}
