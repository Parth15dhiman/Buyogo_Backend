package com.factory.buyogobackend.repository;

import com.factory.buyogobackend.model.Event;
import com.factory.buyogobackend.repository.projection.StatsProjection;
import com.factory.buyogobackend.repository.projection.TopDefectLineProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    @Query("""
        SELECT
            COUNT(e) AS eventsCount,
            COALESCE(SUM(
                CASE WHEN e.defectCount != -1 THEN e.defectCount ELSE 0 END
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

    @Query(""" 
        SELECT
            m.lineId AS lineId,
            COUNT(e) AS eventCount,
            COALESCE(SUM(
                CASE WHEN e.defectCount = -1 THEN 0 ELSE e.defectCount END
            ), 0) AS totalDefects
        FROM Event e
        JOIN Machine m ON e.machineId = m.machineId
        WHERE m.factoryId = :factoryId
          AND e.eventTime >= :from
          AND e.eventTime < :to
        GROUP BY m.lineId
        ORDER BY totalDefects DESC
    """)
    List<TopDefectLineProjection> findTopDefectLines(
            @Param("factoryId") String factoryId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    ) ;
}
