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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


//  TEST 8: Thread-safety (concurrent ingestion)

@SpringBootTest
@AutoConfigureMockMvc
public class Test8 {

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
    void concurrentIngestion_isThreadSafe() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    EventRequestDTO dto = validEvent("E-CONCURRENT");
                    mockMvc.perform(post("/events/batch")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(List.of(dto))))
                            .andExpect(status().isOk());
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        mockMvc.perform(get("/stats")
                        .param("machineId", "M-001")
                        .param("start", LocalDateTime.now().minusHours(1).toString())
                        .param("end", LocalDateTime.now().plusHours(1).toString()))
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
