package com.example.license.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_records")
public class EventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventName;

    @Column(columnDefinition = "TEXT")
    private String eventData;

    private LocalDateTime timestamp = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public EventRecord() {}

    public EventRecord(String eventName, String eventData, License license) {
        this.eventName = eventName;
        this.eventData = eventData;
        this.license = license;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
