package com.plumb5.monitor.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EventDao {

    @Insert
    void insertEvent(EventEntity event);

    @Insert
    void insertEvents(List<EventEntity> events);

    @Query(
            "SELECT * FROM monitoring_events " +
                    "ORDER BY timestamp ASC"
    )
    List<EventEntity> getAllEvents();

    @Query(
            "SELECT * FROM monitoring_events " +
                    "ORDER BY timestamp ASC " +
                    "LIMIT :limit"
    )
    List<EventEntity> getPendingEvents(int limit);

    @Query(
            "SELECT COUNT(*) FROM monitoring_events"
    )
    int getEventCount();

    @Delete
    void deleteEvents(List<EventEntity> events);

    @Delete
    void deleteEvent(EventEntity event);

    @Query(
            "DELETE FROM monitoring_events " +
                    "WHERE eventId = :eventId"
    )
    void deleteById(String eventId);

    @Query(
            "DELETE FROM monitoring_events"
    )
    void deleteAll();

    @Query(
            "DELETE FROM monitoring_events WHERE id IN " +
                    "(SELECT id FROM monitoring_events ORDER BY timestamp ASC LIMIT :limit)"
    )
    void deleteOldest(int limit);

    @Query("DELETE FROM monitoring_events WHERE timestamp <= :timestamp")
    void deleteProcessedEvents(long timestamp);

    @Query("DELETE FROM monitoring_events WHERE timestamp < :threshold")
    void pruneOldEvents(long threshold);
}
