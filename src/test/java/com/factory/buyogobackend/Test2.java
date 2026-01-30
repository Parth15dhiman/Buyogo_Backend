package com.factory.buyogobackend;

import com.factory.buyogobackend.dto.EventRequestDTO;
import com.factory.buyogobackend.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


//  TEST 2: Different payload + newer receivedTime → update happens
@SpringBootTest
@AutoConfigureMockMvc
public class Test2 {

    @Autowired
    private MockMvc mockMvc ;
    @Autowired
    private ObjectMapper objectMapper ;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void clearDb() {
        eventRepository.deleteAll();
    }

    @Test
    void newerReceivedTime_updatesExistingEvent() throws Exception {
        EventRequestDTO e1 = validEvent("E-2");

        EventRequestDTO e2 = validEvent("E-2");
        e2.setDefectCount(5); // changed payload

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(e1))))
                .andExpect(status().isOk());

        Thread.sleep(10); // ensure receivedTime is newer

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(e2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
    }

    private EventRequestDTO validEvent(String id) {
        EventRequestDTO dto = new EventRequestDTO();
        dto.setEventId(id);
        dto.setMachineId("M-001");
        dto.setEventTime(LocalDateTime.now().minusMinutes(5));
        dto.setDurationMs(1000);
        dto.setDefectCount(1);
        return dto;
    }
}
