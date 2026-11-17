package com.example.license.controller;

import com.example.license.entity.EventRecord;
import com.example.license.entity.License;
import com.example.license.repository.EventRecordRepository;
import com.example.license.repository.LicenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringEventController {

    private final LicenseRepository licenseRepository;
    private final EventRecordRepository eventRecordRepository;


    public MonitoringEventController(
            LicenseRepository licenseRepository,
            EventRecordRepository eventRecordRepository) {

        this.licenseRepository =
                licenseRepository;

        this.eventRecordRepository =
                eventRecordRepository;
    }


    // ============================================================
    // SDK EVENT BATCH
    // ============================================================
    //
    // POST:
    //
    // /api/monitoring/events/batch
    //
    // This is the PRIMARY SDK event endpoint.
    //
    // ============================================================

    @PostMapping("/events/batch")
    public ResponseEntity<String> recordEventBatch(
            @RequestHeader("X-Monitoring-Api-Key") String apiKey,
            @RequestBody Object requestBody) {

        System.out.println();
        System.out.println("================================================");
        System.out.println("🚀 [EVENT BATCH] EVENT BATCH RECEIVED");
        System.out.println("================================================");
        System.out.println("API Key: " + apiKey);
        System.out.println("Request Body: " + requestBody);


        // ========================================================
        // VALIDATE API KEY
        // ========================================================

        Optional<License> licenseOpt =
                licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isEmpty()) {

            System.out.println(
                    "❌ [EVENT BATCH] Invalid API key: "
                            + apiKey
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid API key");
        }


        License license =
                licenseOpt.get();


        // ========================================================
        // EXTRACT EVENTS
        // ========================================================

        List<Map<String, Object>> events =
                extractEvents(requestBody);


        if (events.isEmpty()) {

            System.out.println(
                    "⚠️ [EVENT BATCH] No events found"
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("No events found");
        }


        // ========================================================
        // SAVE EVENTS
        // ========================================================

        int savedCount = 0;


        for (Map<String, Object> event : events) {

            if (event == null) {
                continue;
            }


            String eventName =
                    extractEventName(event);


            String eventData =
                    extractEventData(event);


            EventRecord record =
                    new EventRecord(
                            eventName,
                            eventData,
                            license
                    );


            eventRecordRepository.save(record);

            savedCount++;


            System.out.println(
                    "✅ [EVENT BATCH] Saved event #"
                            + savedCount
                            + " | name="
                            + eventName
                            + " | data="
                            + eventData
            );
        }


        // ========================================================
        // VERIFY
        // ========================================================

        if (savedCount == 0) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("No valid events found");
        }


        System.out.println(
                "🚀 [EVENT BATCH] Saved "
                        + savedCount
                        + " events"
        );

        System.out.println("================================================");
        System.out.println();


        return ResponseEntity.ok("OK");
    }


    // ============================================================
    // EXTRACT EVENTS
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractEvents(
            Object requestBody) {

        List<Map<String, Object>> events =
                new ArrayList<>();


        if (requestBody == null) {
            return events;
        }


        // ========================================================
        // CASE 1
        //
        // [
        //     {...},
        //     {...}
        // ]
        // ========================================================

        if (requestBody instanceof List) {

            List<?> list =
                    (List<?>) requestBody;


            for (Object item : list) {

                if (item instanceof Map) {

                    events.add(
                            (Map<String, Object>) item
                    );
                }
            }


            return events;
        }


        // ========================================================
        // CASE 2
        //
        // {
        //     "events": [...]
        // }
        // ========================================================

        if (requestBody instanceof Map) {

            Map<String, Object> map =
                    (Map<String, Object>) requestBody;


            Object eventsObject =
                    map.get("events");


            if (eventsObject instanceof List) {

                List<?> list =
                        (List<?>) eventsObject;


                for (Object item : list) {

                    if (item instanceof Map) {

                        events.add(
                                (Map<String, Object>) item
                        );
                    }
                }


                return events;
            }


            // ====================================================
            // CASE 3
            //
            // {
            //     "event": {...}
            // }
            // ====================================================

            Object eventObject =
                    map.get("event");


            if (eventObject instanceof Map) {

                events.add(
                        (Map<String, Object>) eventObject
                );


                return events;
            }


            // ====================================================
            // CASE 4
            //
            // The map itself is one event.
            // ====================================================

            if (map.containsKey("eventName")
                    || map.containsKey("event_name")
                    || map.containsKey("name")
                    || map.containsKey("type")) {

                events.add(map);

                return events;
            }
        }


        return events;
    }


    // ============================================================
    // EVENT NAME
    // ============================================================

    private String extractEventName(
            Map<String, Object> payload) {

        Object value =
                payload.get("eventName");


        if (value == null) {

            value =
                    payload.get("event_name");
        }


        if (value == null) {

            value =
                    payload.get("name");
        }


        if (value == null) {

            value =
                    payload.get("type");
        }


        if (value == null) {

            return "UNKNOWN_EVENT";
        }


        return String.valueOf(value);
    }


    // ============================================================
    // EVENT DATA
    // ============================================================

    private String extractEventData(
            Map<String, Object> payload) {

        Object value =
                payload.get("eventData");


        if (value == null) {

            value =
                    payload.get("event_data");
        }


        if (value == null) {

            value =
                    payload.get("properties");
        }


        if (value == null) {

            value =
                    payload.get("data");
        }


        // ========================================================
        // IMPORTANT
        //
        // If there is no explicit eventData field,
        // preserve the entire payload.
        // ========================================================

        if (value == null) {

            return payload.toString();
        }


        return String.valueOf(value);
    }
}

