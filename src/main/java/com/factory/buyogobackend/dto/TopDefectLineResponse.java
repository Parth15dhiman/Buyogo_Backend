package com.factory.buyogobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopDefectLineResponse {

    private String lineId;
    private long totalDefects;
    private long eventCount;
    private double defectsPercent;
}
