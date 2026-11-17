package com.example.license.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "beacons")
public class Beacon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String beaconUuid;

    private Integer major;

    private Integer minor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public Beacon() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBeaconUuid() { return beaconUuid; }
    public void setBeaconUuid(String beaconUuid) { this.beaconUuid = beaconUuid; }

    public Integer getMajor() { return major; }
    public void setMajor(Integer major) { this.major = major; }

    public Integer getMinor() { return minor; }
    public void setMinor(Integer minor) { this.minor = minor; }

    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
