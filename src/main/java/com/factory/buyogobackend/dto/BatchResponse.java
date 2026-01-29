package com.factory.buyogobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class BatchResponse {

    private int accepted;
    private int deduped ;
    private int updated ;
    private int rejected ;
    private List<Rejection> rejections ;
}
