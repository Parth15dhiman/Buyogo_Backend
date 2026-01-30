package com.factory.buyogobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class QueryStatsResponse {
    private String machineId;
    private LocalDateTime start;
    private LocalDateTime end ;
    private long eventsCount;
    private long defectsCount;
    private double avgDefectRate;
    private String status;
}
