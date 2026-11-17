package com.example.license.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;
    private String apiKey;
    private String appId;
    private LocalDateTime firstSeen;

    public AppUser() {
    }

    public AppUser(
            String name,
            String email,
            String phone,
            String apiKey,
            String appId) {

        this.name = name;
        this.email = email;
        this.phone = phone;
        this.apiKey = apiKey;
        this.appId = appId;
        this.firstSeen = LocalDateTime.now();
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAppId() {
        return appId;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    // Setters

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }
}