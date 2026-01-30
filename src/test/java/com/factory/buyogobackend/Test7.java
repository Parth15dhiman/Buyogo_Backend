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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//  TEST 7: start inclusive, end exclusive
@SpringBootTest
@AutoConfigureMockMvc
public class Test7 {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void clearDb() {
        eventRepository.deleteAll();
    }

    @Test
    void startInclusive_endExclusive() throws Exception {

        LocalDateTime start = LocalDateTime.now().minusMinutes(10);
        LocalDateTime end = LocalDateTime.now();

        EventRequestDTO inside = validEvent("E-7");
        inside.setEventTime(start);

        EventRequestDTO outside = validEvent("E-8");
        outside.setEventTime(end);

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(inside, outside))))
                .andExpect(status().isOk());


        mockMvc.perform(get("/stats")
                        .param("machineId", "M-001")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsCount").value(1));
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
