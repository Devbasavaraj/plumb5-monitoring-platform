package com.example.license.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_licenses")
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String apiKey; // e.g., "P5-PROD-ALPHA-99"

    private String clientName; 

    private boolean active = true; // YOUR MASTER KILL-SWITCH

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "license", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Geofence> geofences;

    @OneToMany(mappedBy = "license", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Beacon> beacons;

    public License() {}

    public License(String apiKey, String clientName) {
        this.apiKey = apiKey;
        this.clientName = clientName;
        this.active = true;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
