package com.example.license.controller;

import com.example.license.config.AdminAuth;
import com.example.license.entity.InAppMessage;
import com.example.license.entity.License;
import com.example.license.repository.InAppMessageRepository;
import com.example.license.repository.LicenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * In-app messaging (dialogs/forms, banners, local notifications).
 *
 *  GET    /api/inapp/active    (SDK, X-Monitoring-Api-Key)  messages the SDK should cache
 *  GET    /api/inapp/all       (admin)  every message, active or not
 *  POST   /api/inapp/create    (admin)  create a message
 *  POST   /api/inapp/toggle    (admin)  ?id=  switch active on/off
 *  DELETE /api/inapp/{id}      (admin)  delete
 *
 * Admin calls need X-Monitoring-Api-Key (which client) and X-Admin-Token.
 */
@RestController
@RequestMapping("/api/inapp")
public class InAppController {

    private static final Set<String> TYPES = Set.of("dialog", "banner", "notification");
    private static final int MAX_CONTENT_CHARS = 20_000;

    @Autowired
    private LicenseRepository licenseRepository;
    @Autowired
    private InAppMessageRepository repository;
    @Autowired
    private AdminAuth adminAuth;
    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------ SDK

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> active(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey) {
        Optional<License> license = activeLicense(apiKey);
        if (license.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (InAppMessage m : repository.findByLicenseAndActiveTrueOrderByIdDesc(license.get())) {
            messages.add(toSdkJson(m));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        return ResponseEntity.ok(body);
    }

    // ---------------------------------------------------------------- admin

    @GetMapping("/all")
    public ResponseEntity<?> all(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<License> license = activeLicense(apiKey);
        if (license.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid license"));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (InAppMessage m : repository.findByLicenseOrderByIdDesc(license.get())) {
            Map<String, Object> item = toSdkJson(m);
            item.put("active", m.isActive());
            out.add(item);
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Body: { name, type (dialog|banner|notification), trigger, triggerProp?, triggerValue?,
     *         maxImpressions?, minIntervalHours?, content: { ... } }
     */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<License> license = activeLicense(apiKey);
        if (license.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid license"));
        }

        String type = String.valueOf(body.getOrDefault("type", "dialog")).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "type must be dialog, banner or notification"));
        }
        String trigger = text(body.get("trigger"));
        if (trigger == null && !"banner".equals(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "trigger is required"));
        }
        Object content = body.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }
        String contentJson;
        try {
            contentJson = content instanceof String ? (String) content : objectMapper.writeValueAsString(content);
            objectMapper.readTree(contentJson); // must be valid JSON
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is not valid JSON"));
        }
        if (contentJson.length() > MAX_CONTENT_CHARS) {
            return ResponseEntity.badRequest().body(Map.of("error", "content too large"));
        }

        InAppMessage m = new InAppMessage();
        m.setLicense(license.get());
        m.setName(text(body.get("name")));
        m.setType(type);
        m.setTriggerEvent(trigger);
        m.setTriggerProp(text(body.get("triggerProp")));
        m.setTriggerValue(text(body.get("triggerValue")));
        m.setMaxImpressions(Math.max(0, intValue(body.get("maxImpressions"))));
        m.setMinIntervalHours(Math.max(0, intValue(body.get("minIntervalHours"))));
        m.setContentJson(contentJson);
        m.setActive(true);
        return ResponseEntity.ok(toSdkJson(repository.save(m)));
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggle(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestParam Long id) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<InAppMessage> m = owned(apiKey, id);
        if (m.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        m.get().setActive(!m.get().isActive());
        repository.save(m.get());
        return ResponseEntity.ok(Map.of("id", id, "active", m.get().isActive()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<InAppMessage> m = owned(apiKey, id);
        if (m.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.delete(m.get());
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------- helpers

    private Map<String, Object> toSdkJson(InAppMessage m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", m.getId());
        item.put("name", m.getName());
        item.put("type", m.getType());
        item.put("trigger", m.getTriggerEvent());
        item.put("triggerProp", m.getTriggerProp());
        item.put("triggerValue", m.getTriggerValue());
        item.put("maxImpressions", m.getMaxImpressions());
        item.put("minIntervalHours", m.getMinIntervalHours());
        item.put("content", m.getContentJson()); // JSON string; the SDK accepts string or object
        return item;
    }

    private Optional<InAppMessage> owned(String apiKey, Long id) {
        Optional<License> license = activeLicense(apiKey);
        if (license.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(id).filter(m -> m.getLicense() != null
                && Objects.equals(m.getLicense().getId(), license.get().getId()));
    }

    private Optional<License> activeLicense(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return licenseRepository.findByApiKey(apiKey.trim()).filter(License::isActive);
    }

    private ResponseEntity<?> checkAdmin(String adminToken) {
        if (!adminAuth.isConfigured()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin API disabled: set plumb5.admin.token"));
        }
        if (!adminAuth.isAuthorized(adminToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid admin token"));
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : (s.length() > 255 ? s.substring(0, 255) : s);
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
