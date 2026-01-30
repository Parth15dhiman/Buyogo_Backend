## 1. Test Environment

| Component    | Specification                     |
| ------------ |-----------------------------------|
| CPU          | Intel Core i5-1240P (1.70 GHz)    |
| RAM          | 16 GB DDR4                        |
| OS           | Windows 11                        |
| Java Version | OpenJDK 17                        |
| Spring Boot  | 3.2.x                             |
| Database     | H2 (in-memory, default for tests) |
| IDE          | IntelliJ IDEA 2023                |

> Notes: In-memory H2 DB ensures no disk I/O bottleneck; results may vary slightly on a persistent DB.

## 2. Benchmark Methodology
   1. Objective: Measure time to ingest 1000 events in a single batch.
   2. Endpoint tested: `POST /events/batch`
   3. Payload: 1000 `EventRequestDTO` JSON objects with unique `eventId`s.
   4. Measurement approach:
      * Using Postman, I simulated realistic batches of 1000 events and measured round-trip ingestion time.”
      * Alternatively, Java `System.nanoTime()` at start and end of batch processing in the service layer
   5. Batch characteristics:
       * Each event has a duration of 0–6 hours
       * Random `defectCount` (some `-1`)
       * Event times within 15-minute window to simulate real production data

## 3. Measured Results

  | Batch Size  | Time Taken | Notes                                                                                  |
  |-------------|------------|----------------------------------------------------------------------------------------|
  | 1000 events | ~300 ms    | H2 in-memory DB, single-threaded, includes deduplication, DB `saveAll`, and validation |

  This demonstrates that the service meets the requirement: ingest 1000 events in <1 second on a standard laptop.

## 5. Optimizations Attempted

 | Optimization            | Description                                                           | Result                                         |
 |-------------------------|-----------------------------------------------------------------------|------------------------------------------------|
 | Batch persistence       | Used `saveAll()` instead of individual `save()` calls                 | ~10x faster                                    |
 | Single Fetch Call       | Used `findAllById()` instead of individual `findById()` calls         | Reduces DB calls from 1000 → ~1                |
 | Indexed queries         | `@Index(name="idx_machine_time", columnList="machineId, eventTime")`  | Analytics queries and dedupe checks efficient  |
 | SQL-level aggregation   | `COUNT` / `SUM` done in DB, not Java loops                            | Reduces memory footprint and CPU usage         |
