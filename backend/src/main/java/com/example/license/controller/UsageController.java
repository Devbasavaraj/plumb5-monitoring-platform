
package com.example.license.controller;

import com.example.license.entity.EventRecord;
import com.example.license.entity.License;
import com.example.license.entity.TransactionRecord;
import com.example.license.entity.UsageRecord;

import com.example.license.repository.EventRecordRepository;
import com.example.license.repository.LicenseRepository;
import com.example.license.repository.TransactionRecordRepository;
import com.example.license.repository.UsageRecordRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private UsageRecordRepository usageRecordRepository;

    @Autowired
    private EventRecordRepository eventRecordRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================
    // USAGE SUMMARY
    // =========================================================

    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> getUsageSummary() {

        List<UsageRecord> records = usageRecordRepository.findAll();

        Map<String, Integer> summary = records.stream()
                .filter(record -> record.getLicense() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getLicense().getClientName(),
                        Collectors.summingInt(UsageRecord::getEventCount)
                ));

        List<Map<String, Object>> response = summary.entrySet()
                .stream()
                .map(entry -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("client", entry.getKey());
                    result.put("count", entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // USAGE HEARTBEAT
    // =========================================================

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> recordUsageHeartbeat(
            @RequestHeader("X-Monitoring-Api-Key") String apiKey,
            @RequestBody(required = false) Map<String, Object> payload) {

        System.out.println("[USAGE] HEARTBEAT RECEIVED");
        System.out.println("API Key: " + apiKey);
        System.out.println("Payload: " + payload);

        Optional<License> licenseOpt =
                licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isEmpty()) {
            System.out.println("[USAGE] Invalid API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        int eventCount = extractEventCount(payload);

        UsageRecord record = new UsageRecord();
        record.setLicense(licenseOpt.get());
        record.setEventCount(eventCount);

        usageRecordRepository.save(record);

        System.out.println("[USAGE] Saved usage record: " + eventCount);

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // LEGACY EVENT
    // =========================================================

    @PostMapping("/event")
    public ResponseEntity<Void> recordLegacyEvent(
            @RequestHeader("X-Monitoring-Api-Key") String apiKey,
            @RequestBody(required = false) Map<String, Object> payload) {

        System.out.println("[LEGACY EVENT] RECEIVED");
        System.out.println("API Key: " + apiKey);
        System.out.println("Payload: " + payload);

        Optional<License> licenseOpt =
                licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isEmpty()) {
            System.out.println("[LEGACY EVENT] Invalid API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String eventName = extractEventName(payload);
        String eventData = extractEventData(payload);

        EventRecord record = new EventRecord(
                eventName,
                eventData,
                licenseOpt.get()
        );

        eventRecordRepository.save(record);

        System.out.println("[LEGACY EVENT] Saved: " + eventName);

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // GET ALL EVENTS
    // =========================================================

    @GetMapping("/events/all")
    public ResponseEntity<List<EventRecord>> getAllEvents() {
        return ResponseEntity.ok(eventRecordRepository.findAll());
    }

    // =========================================================
    // TRANSACTION
    // =========================================================

    @PostMapping("/transaction")
    public ResponseEntity<Void> recordTransaction(
            @RequestHeader("X-Monitoring-Api-Key") String apiKey,
            @RequestBody(required = false)
            List<Map<String, Object>> items) {

        System.out.println("[TRANSACTION] RECEIVED");
        System.out.println("API Key: " + apiKey);
        System.out.println("Items: " + items);

        Optional<License> licenseOpt =
                licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isEmpty()) {
            System.out.println("[TRANSACTION] Invalid API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (items == null) {
            items = List.of();
        }

        // Serialize the payload into valid JSON.
        final String itemsJson;

        try {
            itemsJson = objectMapper.writeValueAsString(items);
        } catch (JacksonException e) {
            System.err.println(
                    "[TRANSACTION] Serialization failed: " + e.getMessage()
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        TransactionRecord record = new TransactionRecord(
                itemsJson,
                licenseOpt.get()
        );

        transactionRecordRepository.save(record);

        System.out.println(
                "[TRANSACTION] Saved "
                        + items.size()
                        + " transaction items"
        );

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // GET ALL TRANSACTIONS
    // =========================================================

    @GetMapping("/transactions/all")
    public ResponseEntity<List<TransactionRecord>> getAllTransactions() {
        return ResponseEntity.ok(transactionRecordRepository.findAll());
    }

    // =========================================================
    // EXTRACT EVENT COUNT
    // =========================================================

    private int extractEventCount(Map<String, Object> payload) {

        if (payload == null) {
            return 0;
        }

        Object value = payload.get("eventCount");

        if (value == null) {
            value = payload.get("event_count");
        }

        if (value == null) {
            return 0;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            System.out.println("[USAGE] Invalid eventCount: " + value);
            return 0;
        }
    }

    // =========================================================
    // EXTRACT EVENT NAME
    // =========================================================

    private String extractEventName(Map<String, Object> payload) {

        if (payload == null) {
            return "UNKNOWN_EVENT";
        }

        Object value = payload.get("eventName");

        if (value == null) {
            value = payload.get("event_name");
        }

        if (value == null) {
            value = payload.get("name");
        }

        if (value == null) {
            value = payload.get("type");
        }

        if (value == null) {
            return "UNKNOWN_EVENT";
        }

        return String.valueOf(value);
    }

    // =========================================================
    // EXTRACT EVENT DATA
    // =========================================================

    private String extractEventData(Map<String, Object> payload) {

        if (payload == null) {
            return "{}";
        }

        Object value = payload.get("eventData");

        if (value == null) {
            value = payload.get("event_data");
        }

        if (value == null) {
            value = payload.get("properties");
        }

        if (value == null) {
            value = payload.get("data");
        }

        if (value == null) {
            value = payload;
        }

        // Preserve existing string data.
        if (value instanceof String) {
            return (String) value;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            System.err.println(
                    "[LEGACY EVENT] Serialization failed: " + e.getMessage()
            );
            return "{}";
        }
    }
}