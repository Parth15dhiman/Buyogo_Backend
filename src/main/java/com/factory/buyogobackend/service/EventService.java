package com.factory.buyogobackend.service;

import com.factory.buyogobackend.dto.EventRequestDTO;
import com.factory.buyogobackend.dto.BatchResponse;
import com.factory.buyogobackend.dto.Rejection;
import com.factory.buyogobackend.model.Event;
import com.factory.buyogobackend.repository.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
                }
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
}
