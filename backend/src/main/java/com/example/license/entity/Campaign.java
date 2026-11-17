package com.example.license.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String title;

    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public Campaign() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
