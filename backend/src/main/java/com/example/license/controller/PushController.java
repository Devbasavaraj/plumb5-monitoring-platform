package com.example.license.controller;

import com.example.license.config.AdminAuth;
import com.example.license.entity.DeviceToken;
import com.example.license.entity.License;
import com.example.license.repository.DeviceTokenRepository;
import com.example.license.repository.LicenseRepository;
import com.example.license.service.FcmSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Push notification endpoints.
 *
 *  POST /api/push/register  (SDK, X-Monitoring-Api-Key)  store/refresh a device token
 *  POST /api/push/send      (admin: X-Monitoring-Api-Key + X-Admin-Token)  send a campaign
 *  GET  /api/push/stats     (admin)  number of active devices
 *
 * The older /api/campaigns/send (adb based, local testing only) is untouched.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private static final int MAX_FIELD = 255;

    @Autowired
    private LicenseRepository licenseRepository;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private FcmSender fcmSender;
    @Autowired
    private AdminAuth adminAuth;
    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------ register

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestBody Map<String, Object> body) {

        Optional<License> licenseOpt = activeLicense(apiKey);
        if (licenseOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or inactive license");
        }
        String token = str(body, "token", 512);
        if (token == null) {
            return ResponseEntity.badRequest().body("Missing token");
        }

        DeviceToken device = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        device.setToken(token);
        device.setLicense(licenseOpt.get());
        device.setPlatform(orDefault(str(body, "platform", MAX_FIELD), "android"));
        device.setAppId(str(body, "appId", MAX_FIELD));
        // Keep earlier identity if this call carries none (e.g. before login).
        setIfPresent(str(body, "name", MAX_FIELD), device::setUserName);
        setIfPresent(str(body, "email", 320), device::setUserEmail);
        setIfPresent(str(body, "phone", 64), device::setUserPhone);
        setIfPresent(str(body, "deviceModel", MAX_FIELD), device::setDeviceModel);
        setIfPresent(str(body, "osVersion", 64), device::setOsVersion);
        setIfPresent(str(body, "appVersion", 64), device::setAppVersion);
        setIfPresent(str(body, "sdkVersion", 64), device::setSdkVersion);
        device.setActive(true);
        device.setLastSeen(LocalDateTime.now());
        deviceTokenRepository.save(device);
        return ResponseEntity.ok("OK");
    }

    // ---------------------------------------------------------------- send

    /**
     * Body: { title, message, imageUrl?, deepLink?, style? (bigtext|inbox|multiline),
     *         lines? ("a|b|c"), campaignId?, actions? ([{label, link}] or JSON string),
     *         target?: { type: "all" | "email" | "token", value } }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestBody Map<String, Object> body) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<License> licenseOpt = activeLicense(apiKey);
        if (licenseOpt.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid or inactive license");
        }
        if (!fcmSender.isConfigured()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE,
                    "FCM is not configured. Set firebase.credentials.path to a Firebase service-account JSON file.");
        }

        Map<String, String> data = buildPayload(body);
        if (data.get("title") == null && data.get("message") == null) {
            return error(HttpStatus.BAD_REQUEST, "title or message is required");
        }

        License license = licenseOpt.get();
        List<DeviceToken> targets = resolveTargets(license, body.get("target"));
        int sent = 0, failed = 0, deactivated = 0;
        for (DeviceToken device : targets) {
            FcmSender.Result result = fcmSender.send(device.getToken(), data);
            if (result == FcmSender.Result.SENT) {
                sent++;
            } else if (result == FcmSender.Result.INVALID_TOKEN) {
                device.setActive(false);
                deviceTokenRepository.save(device);
                deactivated++;
            } else {
                failed++;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("targeted", targets.size());
        response.put("sent", sent);
        response.put("failed", failed);
        response.put("deactivatedTokens", deactivated);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        Optional<License> licenseOpt = activeLicense(apiKey);
        if (licenseOpt.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid or inactive license");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeDevices", deviceTokenRepository.countByLicenseAndActiveTrue(licenseOpt.get()));
        response.put("fcmConfigured", fcmSender.isConfigured());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------- helpers

    private Map<String, String> buildPayload(Map<String, Object> body) {
        Map<String, String> data = new LinkedHashMap<>();
        putIfPresent(data, "title", str(body, "title", 500));
        // The SDK reads the notification text from the "message" key.
        String text = str(body, "message", 4000);
        putIfPresent(data, "message", text != null ? text : str(body, "body", 4000));
        putIfPresent(data, "imageUrl", str(body, "imageUrl", 2000));
        putIfPresent(data, "deepLink", str(body, "deepLink", 2000));
        putIfPresent(data, "style", str(body, "style", 32));
        putIfPresent(data, "lines", str(body, "lines", 2000));
        putIfPresent(data, "campaignId", str(body, "campaignId", 64));
        Object actions = body.get("actions");
        if (actions instanceof String) {
            putIfPresent(data, "actions", (String) actions);
        } else if (actions != null) {
            data.put("actions", objectMapper.writeValueAsString(actions));
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<DeviceToken> resolveTargets(License license, Object targetObj) {
        String type = "all";
        String value = null;
        if (targetObj instanceof Map) {
            Map<String, Object> target = (Map<String, Object>) targetObj;
            type = String.valueOf(target.getOrDefault("type", "all")).toLowerCase(Locale.ROOT);
            Object v = target.get("value");
            value = v == null ? null : String.valueOf(v).trim();
        }
        if ("email".equals(type) && value != null && !value.isEmpty()) {
            return deviceTokenRepository.findByLicenseAndUserEmailIgnoreCaseAndActiveTrue(license, value);
        }
        if ("token".equals(type) && value != null && !value.isEmpty()) {
            return deviceTokenRepository.findByToken(value)
                    .filter(d -> d.isActive() && d.getLicense() != null
                            && Objects.equals(d.getLicense().getId(), license.getId()))
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        }
        return deviceTokenRepository.findByLicenseAndActiveTrue(license);
    }

    private Optional<License> activeLicense(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return licenseRepository.findByApiKey(apiKey.trim()).filter(License::isActive);
    }

    private ResponseEntity<Map<String, Object>> checkAdmin(String adminToken) {
        if (!adminAuth.isConfigured()) {
            return error(HttpStatus.FORBIDDEN, "Admin API disabled: set plumb5.admin.token");
        }
        if (!adminAuth.isAuthorized(adminToken)) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    private static String str(Map<String, Object> map, String key, int maxLength) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
