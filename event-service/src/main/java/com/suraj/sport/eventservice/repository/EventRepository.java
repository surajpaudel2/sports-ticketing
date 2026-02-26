package com.suraj.sport.eventservice.repository;

import com.suraj.sport.eventservice.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event,Long> {

     Optional<Event> findByNameAndVenueAndEventDate(String eventName, String venue, LocalDateTime eventDate);

}
