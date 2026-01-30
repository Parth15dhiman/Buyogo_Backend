package com.factory.buyogobackend;

import com.factory.buyogobackend.dto.EventRequestDTO;
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

//    TEST 4: Invalid duration rejected

@SpringBootTest
@AutoConfigureMockMvc
public class Test4 {

    @Autowired
    private MockMvc mockMvc ;
    @Autowired
    private ObjectMapper objectMapper ;

    @Test
    void invalidDuration_isRejected() throws Exception {
        EventRequestDTO bad = validEvent("E-4");
        bad.setDurationMs(-10);

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(bad))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(1));
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
