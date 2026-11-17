package com.example.license.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A registered mobile device that can receive push notifications. */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 512)
    private String token;

    private String platform;
    private String appId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String sdkVersion;

    /** Set to false when FCM reports the token as no longer valid. */
    private boolean active = true;

    private LocalDateTime firstSeen = LocalDateTime.now();
    private LocalDateTime lastSeen = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public DeviceToken() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(String sdkVersion) { this.sdkVersion = sdkVersion; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getFirstSeen() { return firstSeen; }
    public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
