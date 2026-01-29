package com.factory.buyogobackend.repository;

import com.factory.buyogobackend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, String> {
}
