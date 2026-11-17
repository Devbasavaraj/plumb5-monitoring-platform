package com.example.license.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One message in a visitor/agent chat conversation. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String appId;
    private String sessionId;

    /** "user" (the app visitor) or "agent" (a support person). */
    private String sender;
    private String senderName;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String text;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
