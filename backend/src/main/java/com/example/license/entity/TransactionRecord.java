package com.example.license.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_records")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    private LocalDateTime timestamp = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    public TransactionRecord() {}

    public TransactionRecord(String itemsJson, License license) {
        this.itemsJson = itemsJson;
        this.license = license;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
}
