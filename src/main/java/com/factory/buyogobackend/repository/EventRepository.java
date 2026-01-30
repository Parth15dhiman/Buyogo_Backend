package com.factory.buyogobackend.repository;

import com.factory.buyogobackend.model.Event;
import com.factory.buyogobackend.repository.projection.StatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    @Query("""
        SELECT
            COUNT(e) AS eventsCount,
            COALESCE(SUM(
                CASE WHEN e.defectCount != -1 THEN 1 ELSE 0 END
            ), 0) AS defectsCount
        FROM Event e
        WHERE e.machineId = :machineId
          And e.eventTime >= :start
          AND e.eventTime < :end        
    """)
    StatsProjection fetchStats(
            @Param("machineId") String machineId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
            );
}
