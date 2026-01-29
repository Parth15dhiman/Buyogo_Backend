package com.factory.buyogobackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EventRequestDTO {

    private String eventId ;

    private String machineId ;

    private LocalDateTime eventTime ;

    private int durationMs;

    private int defectCount;
}
