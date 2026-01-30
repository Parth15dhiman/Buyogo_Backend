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

/*    TEST 3: Different payload + older receivedTime → ignored

       NOTE : Since it is mentioned in the assignment PDF that receivedTime is only for reference in the json,
          but would be set by our service and data if any in the API request payload would be ignored. So it
          changes the receiveTime of older is changed to current time. Otherwise, if it does not change the
          test case is passed
*/

@SpringBootTest
@AutoConfigureMockMvc
public class Test3 {
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
    void olderReceivedTime_isIgnored() throws Exception {
        EventRequestDTO e1 = validEvent("E-3");

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(e1))))
                .andExpect(status().isOk());

        EventRequestDTO older = validEvent("E-3");
        older.setDefectCount(99);
        // this receiveTime will automatically change to time when it reaches to backend as per assignment requirements.
        older.setEventTime(LocalDateTime.now().minusHours(2)); // older

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(older))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1)); // Intentionally mentioned updated 1.
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
