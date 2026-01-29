package com.factory.buyogobackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "events",
    indexes = @Index(name = "idx_machine_time", columnList = "machineId, eventTime")
)
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class Event {
    @Id
    private String eventId ;

    @Column(nullable = false)
    private String machineId ;

    @Column(nullable = false)
    private LocalDateTime eventTime ;

    @Column(nullable = false)
    private LocalDateTime receivedTime ;

    @Column(nullable = false)
    private int durationMs;

    @Column(nullable = false)
    private int defectCount;
}
