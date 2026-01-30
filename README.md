
## Overview
This service ingests high-volume machine events in batches, performs deduplication and updates based on assignment rules,
and exposes analytical APIs for querying event statistics and defect trends.  
The system is designed to be thread-safe, performant, and deterministic under concurrent ingestion.


## 1. Architecture
The system follows a layered monolithic architecture using Java 17 and the Spring Boot framework.
It follows a classic Layered Architecture to ensure a clean separation of concerns and maintainability.
This design was chosen to keep the solution simple, testable, and deterministic.
The application is stateless, meaning all state is persisted in the database, which allows safe horizontal scaling if required.

                    ┌────────────┐
                    │   Client   │
                    └─────┬──────┘
                      HTTP (JSON)
                    ┌─────▼──────┐
                    │ Controller │
                    └─────┬──────┘
                         DTOs
                    ┌─────▼──────┐
                    │  Service   │
                    └─────┬──────┘
                       Entities
                    ┌─────▼──────┐
                    │ Repository │
                    └─────┬──────┘
                         SQL
                    ┌─────▼──────┐
                    │  Database  │
                    │    (H2)    │
                    └────────────┘

 ### Controller Layer
   * Exposes REST endpoints (/events/batch, /stats, /stats/top-defect-lines)
   * Performs request validation
   * Converts HTTP input into DTOs
   * Returns HTTP responses (JSON)

 ### Service Layer
   * Implements core business logic
   * Performs:
        * Validation rules
          1. Deduplication
          2. Update decision logic
       * Batch preparation
       * Coordinates between repositories
       * Key design decision
         1. Deduplication is done in memory first (HashMap) to reduce DB calls
         2. Database is consulted only once per batch
       * Reasons
           * Keeps ingestion fast
           * Avoids per-event DB round trips
           * Makes logic deterministic and testable

 ### Repository Layer
   * Encapsulates all database access
     * Uses:
        * Spring Data JPA for CRUD
        *  JPQL / SQL for aggregation queries
        * Aggregation queries (COUNT, SUM, GROUP BY) are executed in SQL

          * Reason
               * Databases are optimized for aggregation
               *  Avoids loading large datasets into memory
               * Ensures consistent performance for analytics APIs
 ### Database Layer
   * Relational database (H2 for local/testing)
   * Why relational 
        * Strong consistency guarantees
        * Built-in concurrency control
        * Efficient indexing and aggregation
    
   * Role in architecture
        * Source of truth
        * Provides thread safety via:
            * Primary key constraints
            * Transaction isolation
            * Atomic updates


 ### Data Flow

   #### Batch Ingestion
        POST /events/batch
                ↓
        Controller validates input
                ↓
        Service validates + dedupes batch in memory
                ↓
        Service fetches existing records in one query
                ↓
        Service decides insert / update / ignore
                ↓
        Repository.saveAll()
                ↓
        Database commits atomically
    
   * This flow ensures:
        * Minimal DB interactions
        * Deterministic deduplication
        * Atomic batch processing

   #### Analytics Queries
        GET stats
        GET /stats/top-defect-lines
                ↓
        Controller validates query params
                ↓
        Service delegates to repository
                ↓
        Repository executes SQL aggregation
                ↓
        Service computes derived metrics (percentages)
                ↓
        Controller returns JSON

    zAnalytics APIs are read-only and do not interfere with ingestion.

## 2. Deduplication & Update Logic

 Problem Statement
   The system may receive:
   * Duplicate events with the same `eventId`
     * Events arriving out of order
     * Multiple versions of the same event with different payloads
     * Concurrent ingestion attempts for the same `eventId`

   The goal is to ensure:
     * Only one record exists per `eventId`
     * The stored record always represents the most authoritative version
     * Processing is deterministic and repeatable

 ### Event Identity
  Each event is uniquely identified by: `eventId`
  This field:
   * Is provided by the producer
    * Is treated as the logical identity of an event
    * Is enforced as a primary key at the database level
  This guarantees no duplicate rows for the same event.

 ### Payload Definition
  An event payload is considered to consist of:
   * `machineId`
   * `eventTime`
   * `durationMs`
   * `defectCount`

  The following fields are explicitly excluded from payload comparison:
   * `receivedTime` (assigned by the service)
   * Any client-provided timestamps not considered authoritative

 ### Payload Comparison Strategy
   Two events with the same `eventId` are considered identical if all payload fields match exactly.
   In code, this is implemented as a field-by-field comparison, ensuring clarity and predictability:
   This avoids false positives and makes the behavior easy to reason about and test.

 ### Deduplication Rules
  For events with the same `eventId`, the system applies the following rules:

  | Scenario                                 | Action                  |
  | ---------------------------------------- | ----------------------- |
  | Same payload as existing record          | Deduplicated (ignored)  |
  | Different payload + newer `receivedTime` | Existing record updated |
  | Different payload + older `receivedTime` | Ignored                 |

 ### Determining the “Winning” Record
  The authoritative version of an event is determined by: max(receivedTime), event with the max receiveTime is updated.
  * `receivedTime` is set by the service at ingestion time
  * Any client-provided `receivedTime` is ignored 

 ### In-Batch Deduplication
  Within a single batch:
  * Events are first checked whether two or more events have same eventId
  * A `HashMap<eventId, Event>` is used

  Processing logic:
  * Iterate through incoming events
    * For each `eventId`:
      1. If not seen before → store
      2. If seen before then:
        * Compare payloads
        * Keep the newer `receivedTime`

  This avoids:
   * Redundant database operations and increasing performance
   * Conflicting updates within the same batch

 ### Database-Level Deduplication
   After in-memory processing:
   * Existing records are fetched in a single query
   * Each candidate event is compared against the stored record

   The same rules are applied before deciding:
   * Insert - if not present in DB
   * Update - if present in DB but have different payload
   * Ignore - if present in DB having same payload
    
   The database primary key ensures that:
   * Concurrent inserts for the same `eventId` cannot create duplicates
   * Updates are atomic and consistent

 ### Why This Approach Was Chosen

  | Design Choice                   | Reason                            |
  | ------------------------------- | --------------------------------- |
  | In-memory batch dedupe          | Minimizes DB calls                |
  | Field-by-field comparison       | Predictable and testable          |
  | Last-write-wins                 | Deterministic conflict resolution |
  | DB PK enforcement               | Strong concurrency guarantee      |

## 2. Deduplication & Update Logic

### Problem Statement

The system may receive:

* Duplicate events with the same `eventId`
* Events arriving out of order
* Multiple versions of the same event with different payloads
* Concurrent ingestion attempts for the same `eventId`

The goal is to ensure:

* Only one record exists per `eventId`
* The stored record always represents the most authoritative version
* Processing is deterministic and repeatable

---

### Event Identity

Each event is uniquely identified by:

* `eventId`

This field:

* Is provided by the producer
* Is treated as the logical identity of an event
* Is enforced as a primary key at the database level

This guarantees no duplicate rows for the same event.

### Payload Definition

An event payload is considered to consist of:

* `machineId`
* `eventTime`
* `durationMs`
* `defectCount`

The following fields are explicitly excluded from payload comparison:

* `receivedTime` (assigned by the service)
* Any client-provided timestamps not considered authoritative

### Payload Comparison Strategy

Two events with the same `eventId` are considered identical if all payload fields match exactly.

In code, this is implemented as a field-by-field comparison, ensuring clarity and predictability:

* No fuzzy matching
* No partial equality
* No hashing shortcuts

This avoids false positives and makes the behavior easy to reason about and test.

### Deduplication Rules

For events with the same `eventId`, the system applies the following rules:

| Scenario                                 | Action                  |
| ---------------------------------------- | ----------------------- |
| Same payload as existing record          | Deduplicated (ignored)  |
| Different payload + newer `receivedTime` | Existing record updated |
| Different payload + older `receivedTime` | Ignored                 |

### Determining the “Winning” Record

The authoritative version of an event is determined by:

```
max(receivedTime)
```

Key points:

* `receivedTime` is set by the service at ingestion time
* Any client-provided `receivedTime` is ignored
* This prevents clock skew from producers influencing correctness

This approach ensures:

* Deterministic conflict resolution
* Consistent behavior across concurrent requests
* Clear auditability

### In-Batch Deduplication

Within a single batch:

* Events are first deduplicated in memory
* A `HashMap<eventId, Event>` is used

Processing logic:

* Iterate through incoming events
* For each `eventId`:

    * If not seen before → store
    * If seen before:

        * Compare payloads
        * Keep the newer `receivedTime`

This avoids:

* Redundant database operations
* Conflicting updates within the same batch

### Database-Level Deduplication

After in-memory processing:

* Existing records are fetched in a single query
* Each candidate event is compared against the stored record

The same rules are applied before deciding:

* Insert
* Update
* Ignore

The database primary key ensures that:

* Concurrent inserts for the same `eventId` cannot create duplicates
* Updates are atomic and consistent

### Why This Approach Was Chosen

| Design Choice                   | Reason                            |
| ------------------------------- | --------------------------------- |
| In-memory batch dedupe          | Minimizes DB calls                |
| Field-by-field comparison       | Predictable and testable          |
| Service-assigned `receivedTime` | Avoids clock skew                 |
| Last-write-wins                 | Deterministic conflict resolution |
| DB PK enforcement               | Strong concurrency guarantee      |

### Trade-offs

* Payload comparison is explicit rather than hash-based
* Slightly more CPU per event
* Much clearer semantics
* Last-write-wins may overwrite intermediate versions

Accepted per problem constraints

---

## 3. Thread Safety & Concurrency Control

  Concurrency Problem
   The system must safely handle:
   * Multiple threads ingesting batches concurrently
   * Multiple events with the same `eventId`
   * Out-of-order arrival of events
   * Concurrent reads (analytics) while ingestion is ongoing

  The system must guarantee:
   * No duplicate records
   * No lost updates
   * Deterministic final state

 ### Database Constraints as the First Line of Defense
   * Primary Key Constraint
     * `eventId` is defined as the primary key
     * Ensures at most one row per `eventId`

  Effect:
   * Concurrent inserts for the same `eventId` cannot both succeed
   * Database enforces uniqueness atomically

 ### Transactional Semantics
   Each batch ingestion is processed inside a transactional boundary.
   This ensures:
   * Either all valid events in the batch are persisted, or none are
   * No partial writes
   * Atomic visibility of updates

  Even if two transactions race:
  * One will commit first
  * The other will re-evaluate state based on DB constraints

 ### Deterministic Conflict Resolution
   When conflicts occur (same `eventId`):
   * The system compares payloads
   * Uses `receivedTime` to determine the winning record
  Key property:
  * `receivedTime` is assigned by the service

 ### In-Memory Structures & Thread Safety
  In-memory data structures (e.g., `HashMap` used for batch dedupe):
  * Are request-scoped
  * Not shared across threads
  * Created per batch
  * 
  This avoids:
  * Shared mutable state
  * Race conditions in application memory

 ### What Happens Under Concurrent Ingestion?
 Scenario:
 * Two threads ingest events with the same `eventId`
 Outcome:
 * Both perform in-memory validation
 * Both attempt persistence
 * Database primary key enforces uniqueness
 * Service logic ensures the newer `receivedTime` wins
 Result:
 * No duplicate rows
 * Correct final state
 * No data corruption

## 4. Data Model
Two primary tables are used:
* `events`
* `machines`

 ### Events Table
   The `events` table stores **time-series machine events** and serves as the system’s core dataset.

   #### Schema
   ```sql
    events (
      event_id        VARCHAR PRIMARY KEY,
      machine_id      VARCHAR NOT NULL,
      event_time      TIMESTAMP NOT NULL,
      received_time   TIMESTAMP NOT NULL,
      duration_ms     INT NOT NULL,
      defect_count    INT NOT NULL
    )
   ```
   #### Column Rationale
     event_id: Logical identity of an event; enforces uniqueness 
     machine_id: Links event to machine metadata                   
     event_time: Time at which the event occurred                  
     received_time: Time event was ingested (service-assigned)        
     duration_ms: Duration of event in milliseconds                 
     defect_count`: Number of defects (`-1` means unknown)

 ### Machines Table
   The `machines` table stores **organizational metadata**.

   #### Schema
   ```sql
        machines (
          machine_id   VARCHAR PRIMARY KEY,
          factory_id   VARCHAR,
          line_id      VARCHAR
        )
   ```

   #### Purpose
   * Separates machine metadata from high-volume event data
   * Allows flexible aggregation across:
       * Factories
       * Production lines
       * Individual machines
       * 

 ### Indexing Strategy
   Indexes are defined to support critical query patterns.
   ```sql
    INDEX idx_machine_time (machine_id, event_time)
   ```

   #### Why This Index?
    * Filters efficiently by `machine_id`
    * Enables fast time-range scans on `event_time`
    * Supports both:
        * High-throughput ingestion lookups
        * Analytical queries

## 5. Performance Strategy
The system is designed to process a batch of **1000 events within 1 second** on a standard laptop by minimizing I/O and leveraging batch operations.

 ### Key Techniques Used

   1. Batch Persistence
      * Events are saved using `saveAll()` instead of individual `save()` calls
      * Reduces database round-trips from 1000 to 1
   2. In-Memory Deduplication
      * Events are deduplicated in memory using a `HashMap`
      * Prevents unnecessary database writes for duplicates within the same batch
   3. Single Read from Database
      * Existing events are fetched in one query using `findAllById`
      * Avoids N+1 database access patterns
   4. SQL-Based Aggregation
      * Analytics queries use `COUNT`, `SUM`, and `GROUP BY` in SQL
      * Avoids loading large datasets into application memory
   5. Indexing
      * Composite index on `(machineId, eventTime)`
      * Ensures fast time-window scans for queries

## 6. Edge Cases & Assumptions

  ### Edge Cases Handled
  1. Duplicate `eventId`
     * Identical payload → deduplicated
     * Different payload → resolved using `receivedTime`
  2. Out-of-order events
     * Newer `receivedTime` always wins
     * Older versions are ignored
  3. Invalid duration
     * Events with negative duration or duration > 6 hours are rejected
  4. Future event time
     * Events more than 15 minutes in the future are rejected
     * Small future buffer allows for clock skew across machines
  5. Unknown defect count
     * `defectCount = -1` is ignored in defect aggregations 
  6. Time window boundaries
     * `start` is inclusive
     * `end` is exclusive
  7. Partial batch failures
     * Invalid events are rejected individually
     * Valid events in the same batch are still processed

  ### Trade-offs
   1. Last-write-wins
      * Simpler and deterministic
      * Does not preserve historical versions
   2. Normalized schema
       * Slight join cost in analytics
       * Improved data consistency and flexibility
   3. Some events may be dropped instead of partially accepted

## 7. Setup & Run Instructions

  ### Prerequisites
   1. Java 17 or higher
   2. Maven 3.8+
   3. Optional: IDE (IntelliJ, Eclipse, VS Code) for development

  ### Clone the Project
   ```bash
    git clone https://github.com/Parth15dhiman/Buyogo_Backend.git
    cd buyogobackend
   ```

  ### Build the Project
   ```bash
    ./mvnw clean install
   ```
   This compiles the code, runs tests, and packages the application.

  ### Run the Application
   ```bash
    ./mvnw spring-boot:run
   ```
   * The server starts on `http://localhost:8080` by default
   * Uses H2 in-memory database for simplicity
   * No external database setup required

  ### API Endpoints

| Endpoint                  | Method |      RequestPara.      | Description                                       |
|---------------------------|:------:|:----------------------:| ------------------------------------------------- |
| `/events/batch`           |  POST  |          N/A           | Batch ingestion of events                         |
| `/stats`                  |  GET   | machineId, start & end | Query stats for a machine in a time window        |
| `/stats/top-defect-lines` |  GET   |  factoryId, from & to  | List top lines with highest defects for a factory |

  ### Notes
  * The H2 database is in-memory; all data is lost on application restart
  * Use JSON payloads as per `EventRequestDTO` for POST requests
  * Query parameters for GET requests follow LocalDateTime


## 8. What You Would Improve With More Time
While the current implementation meets functional and performance requirements, given additional time, several improvements could make the system more scalable, maintainable, and resilient.

   1. Historical Event Tracking
      * Currently, only the latest version of each `eventId` is stored
      * Improvement:
          * Maintain a history table for audit purposes
          * Store previous payloads and `receivedTime`
      * Benefits:
          * Traceability
          * Analytics over updates
      * Tradeoff:
          * Increased storage
          * Slightly slower writes

   2. Horizontal Scaling & Load Distribution
      * Deploy the service in multiple instances behind a load balancer
      * Use distributed cache or database transactions to maintain consistency
      * Could integrate sharding or partitioning in the database for very high volume

   3. Advanced Analytics & Caching
      * Precompute frequent statistics (e.g., top defect lines, average defect rates)
      * Cache results in memory or Redis
      * Benefits:
          * Reduced query latency
          * Better experience for dashboards and high-traffic analytics
      * Tradeoff:
          * Cache invalidation complexity

   4. Observability & Metrics
      * Add structured logging, metrics, and tracing (Prometheus + Grafana)
      * Track:
          * Batch ingestion times
          * Rejection rates
          * Concurrency conflicts
      * Benefits:
          * Easier debugging
          * Detect anomalies early

   5. Improved Validation & Schema Evolution
      * Move validation logic to a centralized service or schema registry
      * Support future fields in `EventRequestDTO` without breaking ingestion
      * Benefits:
          * Cleaner code
          * Forward-compatible design

   6. Fault-Tolerant / Retry Mechanism
      * Implement retries for transient failures in database or network
      * Queue failed events for later ingestion
      * Benefits:
          * Higher reliability
          * No data loss
      * Tradeoff:
          * Requires monitoring and alerting for stuck events
