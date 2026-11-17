package com.example.license.controller;

import com.example.license.entity.AppUser;
import com.example.license.entity.License;
import com.example.license.repository.AppUserRepository;
import com.example.license.repository.LicenseRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final AppUserRepository repository;
    private final LicenseRepository licenseRepository;

    public UserController(
            AppUserRepository repository,
            LicenseRepository licenseRepository) {
        this.repository = repository;
        this.licenseRepository = licenseRepository;
    }

    @PostMapping("/report")
    public ResponseEntity<String> reportUser(
            @RequestHeader(
                    value = "X-Monitoring-Api-Key",
                    required = false
            ) String apiKeyHeader,
            @RequestBody Map<String, Object> payload) {

        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing API key");
        }

        String authenticatedApiKey = apiKeyHeader.trim();

        Optional<License> licenseOptional =
                licenseRepository.findByApiKey(authenticatedApiKey);

        if (licenseOptional.isEmpty()
                || !licenseOptional.get().isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or inactive license");
        }

        String name = getString(payload, "name");
        String email = getString(payload, "email");
        String phone = getString(payload, "phone");
        String appId = getString(payload, "appId");

        if (email == null ||
                !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest().body("Invalid email");
        }

        if (appId == null || appId.isBlank()) {
            return ResponseEntity.badRequest().body("Missing appId");
        }

        email = email.trim();
        appId = appId.trim();

        if (email.length() > 320 || appId.length() > 255) {
            return ResponseEntity.badRequest()
                    .body("Invalid email or appId length");
        }

        Optional<AppUser> existingUser =
                repository.findByEmailAndApiKeyAndAppId(
                        email, authenticatedApiKey, appId);

        if (existingUser.isPresent()) {
            AppUser user = existingUser.get();

            if (name != null && !name.isBlank()) {
                user.setName(name.trim());
            }

            if (phone != null && !phone.isBlank()) {
                user.setPhone(phone.trim());
            }

            user.setEmail(email);
            user.setApiKey(authenticatedApiKey);
            user.setAppId(appId);

            repository.save(user);
            return ResponseEntity.ok("User updated successfully");
        }

        AppUser user = new AppUser(
                name == null ? null : name.trim(),
                email,
                phone,
                authenticatedApiKey,
                appId
        );

        repository.save(user);
        return ResponseEntity.ok("User saved successfully");
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);

        if (value == null) {
            return null;
        }

        String result = String.valueOf(value).trim();

        if (result.isEmpty() || "null".equalsIgnoreCase(result)) {
            return null;
        }

        return result;
    }
}