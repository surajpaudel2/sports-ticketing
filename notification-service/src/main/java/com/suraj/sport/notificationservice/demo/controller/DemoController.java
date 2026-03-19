package com.suraj.sport.notificationservice.demo.controller;

import com.suraj.sport.notificationservice.demo.dto.BookingDto;
import com.suraj.sport.notificationservice.demo.dto.BookingDto2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final BookingDto bookingDto;
    private final BookingDto2 bookingDto2;

    @GetMapping
    private ResponseEntity<BookingDto> getBooking() {
        System.out.println(bookingDto.getAddress() + " " + bookingDto.getName() + " " );
        System.out.println("Inside demo controller");
        return ResponseEntity.ok(bookingDto);
    }

    @GetMapping("/v2")
    private ResponseEntity<BookingDto2> getBookingV2() {
        System.out.println("Inside demo controller v2");
        return ResponseEntity.ok(bookingDto2);
    }
}
