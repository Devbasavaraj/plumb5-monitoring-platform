package com.example.license.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "geofences")
public class Geofence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String geofenceId; 

    private String name;

    private Double latitude;

    private Double longitude;

    private Float radius;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public Geofence() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGeofenceId() { return geofenceId; }
    public void setGeofenceId(String geofenceId) { this.geofenceId = geofenceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Float getRadius() { return radius; }
    public void setRadius(Float radius) { this.radius = radius; }

    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
