package com.factory.buyogobackend.service;

import com.factory.buyogobackend.dto.*;
import com.factory.buyogobackend.model.Event;
import com.factory.buyogobackend.repository.EventRepository;
import com.factory.buyogobackend.repository.projection.StatsProjection;
import com.factory.buyogobackend.repository.projection.TopDefectLineProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository ;

    @Transactional
    public BatchResponse processBatch(List<EventRequestDTO> events){

        Map<String, Event> validEventsMap = new HashMap<>() ;
        List<Rejection> rejections = new ArrayList<>() ;

        LocalDateTime now = LocalDateTime.now();
        int deduped = 0 ;
        int updated = 0 ;

        for(EventRequestDTO dto : events){

            if( isValid(dto, rejections , now) ){

                Event event = maptoEvent(dto, now) ;
                String id = dto.getEventId();
                if( validEventsMap.containsKey(id) && samePayLoad( event, validEventsMap.get( dto.getEventId())) ){
                        deduped++ ;
                }

                validEventsMap.put(id, event) ;
            }


        }

        List<Event> eventsInDB = eventRepository.findAllById(validEventsMap.keySet());
        Map<String, Event> dbEventMap = eventsInDB.stream().collect(Collectors.toMap(Event::getEventId, e -> e));

        List<Event> toSave = new ArrayList<>() ;
        for( Event event : validEventsMap.values() ) {

            String id = event.getEventId();
            if( dbEventMap.containsKey(id) ) {

                Event dbEvent = dbEventMap.get(id);
                if( samePayLoad(event, dbEvent) ) {
                    deduped++ ;
                    continue;
                }else if(event.getReceivedTime().isBefore( dbEventMap.get(id).getReceivedTime() ) )
                    continue;

                updated++ ;
            }

            toSave.add(event) ;
        }

        eventRepository.saveAll(toSave) ;

        return new BatchResponse(
                toSave.size(), deduped, updated, rejections.size(), rejections
        );
    }

    private boolean samePayLoad(Event event1, Event event2) {
        try {
            return (
                    event1.getMachineId().equals(event2.getMachineId() )&&
                            event1.getEventTime().equals(event2.getEventTime() ) &&
                            event1.getDurationMs() == event2.getDurationMs() &&
                            event1.getDefectCount() == event2.getDefectCount()
            ) ;
        } catch (Exception e) {
            log.error("error in calculating payload matching", e);
            return false;
        }

    }

    private Event maptoEvent(EventRequestDTO dto, LocalDateTime now) {
        return new Event(
                dto.getEventId(),
                dto.getMachineId(),
                dto.getEventTime(),
                now,
                dto.getDurationMs(),
                dto.getDefectCount()
        );
    }

    private boolean isValid(EventRequestDTO dto, List<Rejection> rejections, LocalDateTime now) {


        if( dto.getEventTime() == null ) {
            rejections.add(new Rejection(dto.getEventId(), "NULL_EVENT_TIME")) ;
            return false ;
        }

        if( dto.getEventTime().isAfter(now.plusMinutes(15) ) ){
            rejections.add(new Rejection(dto.getEventId(), "EVENT_TIME_TOO_FAR_IN_FUTURE")) ;
            return false ;
        }

        if( dto.getDurationMs() < 0 || dto.getDurationMs() > 6*60*60*1000) {
            rejections.add(new Rejection(dto.getEventId(), "INVALID_DURATION")) ;
            return false ;
        }

        if( dto.getEventId() == null ) {
            rejections.add(new Rejection(null, "NULL_EVENT_ID")) ;
            return false ;
        }

        if( dto.getMachineId() == null ) {
            rejections.add(new Rejection(dto.getEventId(), "NULL_MACHINE_ID")) ;
            return false ;
        }

        return true ;
    }

    public QueryStatsResponse getStats(String machineId, LocalDateTime start, LocalDateTime end) {
        try {
            StatsProjection stats = eventRepository.fetchStats(machineId, start, end);
            long eventsCount = stats.getEventsCount();
            long defectsCount = stats.getDefectsCount();

            double windowSeconds = Duration.between(start, end).getSeconds();
            double windowHours = windowSeconds / 3600.0 ;

            double avgDefectRate = (windowHours > 0) ? defectsCount / windowHours : 0.0 ;

            String status = (avgDefectRate < 2.0 ) ? "Healthy" : "Warning" ;

            return new QueryStatsResponse(
                    machineId, start, end, eventsCount, defectsCount, avgDefectRate, status
            ) ;

        } catch (Exception e){
            log.error("error in querying stats", e);
            return null ;
        }
    }

    public List<TopDefectLineResponse> getTopDefectLines(String factoryId,LocalDateTime from, LocalDateTime to, int limit){

        List<TopDefectLineProjection> topDefectLines = eventRepository.findTopDefectLines(factoryId, from, to, PageRequest.of(0, limit));
        List<TopDefectLineResponse> response = new ArrayList<>() ;

        for(TopDefectLineProjection proj : topDefectLines){

            long totalDefects = proj.getTotalDefects();
            long eventCount = proj.getEventCount();
            double defectsPercent = (eventCount == 0) ? 0.0 : round((totalDefects * 100.0) / eventCount ) ;
            response.add( new TopDefectLineResponse(
                    proj.getLineId(), proj.getTotalDefects(), proj.getEventCount(), defectsPercent) ) ;
        }

        return response ;
    }
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

}
