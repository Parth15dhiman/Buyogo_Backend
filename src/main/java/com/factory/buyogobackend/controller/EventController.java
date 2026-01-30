package com.factory.buyogobackend.controller;

import com.factory.buyogobackend.dto.EventRequestDTO;
import com.factory.buyogobackend.dto.BatchResponse;
import com.factory.buyogobackend.dto.QueryStatsResponse;
import com.factory.buyogobackend.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class EventController {

    @Autowired
    private EventService eventService;

    @PostMapping("/events/batch")
    public ResponseEntity<BatchResponse> injectBatch(@RequestBody List<EventRequestDTO> events){

        if(events == null || events.isEmpty() ){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        BatchResponse response = eventService.processBatch(events);
        return ResponseEntity.ok(response) ;
    }

    @GetMapping("/stats")
    public ResponseEntity<QueryStatsResponse> fetchQueryStats(
            @RequestParam String machineId, @RequestParam LocalDateTime start, @RequestParam LocalDateTime end
            )
    {
        QueryStatsResponse stats = eventService.getStats(machineId, start, end);
        return ResponseEntity.ok(stats) ;
    }
}
