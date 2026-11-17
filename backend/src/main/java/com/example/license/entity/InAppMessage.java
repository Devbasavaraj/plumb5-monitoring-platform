package com.example.license.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A server-defined in-app message (dialog/form, banner or local notification)
 * that the SDK downloads from /api/inapp/active and shows when its trigger
 * event happens.
 */
@Entity
@Table(name = "inapp_messages")
public class InAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** dialog | banner | notification */
    private String type = "dialog";

    /** An SDK event name (APP_FOREGROUND, GEOFENCE_ENTER, BEACON_DETECTED, ...) or MANUAL:key. */
    @Column(name = "trigger_event")
    private String triggerEvent;

    /** Optional event property that must equal triggerValue (e.g. geofence_id = 1). */
    private String triggerProp;
    private String triggerValue;

    /** 0 = unlimited. */
    private int maxImpressions = 0;
    private int minIntervalHours = 0;

    /** Layout/content JSON understood by the SDK renderer. */
    @Column(columnDefinition = "TEXT")
    private String contentJson;

    private boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public InAppMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public String getTriggerProp() { return triggerProp; }
    public void setTriggerProp(String triggerProp) { this.triggerProp = triggerProp; }
    public String getTriggerValue() { return triggerValue; }
    public void setTriggerValue(String triggerValue) { this.triggerValue = triggerValue; }
    public int getMaxImpressions() { return maxImpressions; }
    public void setMaxImpressions(int maxImpressions) { this.maxImpressions = maxImpressions; }
    public int getMinIntervalHours() { return minIntervalHours; }
    public void setMinIntervalHours(int minIntervalHours) { this.minIntervalHours = minIntervalHours; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
