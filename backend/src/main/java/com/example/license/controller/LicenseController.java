package com.example.license.controller;

import com.example.license.entity.License;
import com.example.license.repository.LicenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/license")
public class LicenseController {

    @Autowired
    private LicenseRepository repository;

    @GetMapping("/all")
    public ResponseEntity<List<License>> getAllLicenses() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping("/toggle")
    public ResponseEntity<License> toggleLicense(@RequestParam Long id) {
        Optional<License> licenseOpt = repository.findById(id);
        if (licenseOpt.isPresent()) {
            License license = licenseOpt.get();
            license.setActive(!license.isActive());
            return ResponseEntity.ok(repository.save(license));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/create")
    public ResponseEntity<License> createLicense(@RequestBody Map<String, String> payload) {
        String name = payload.get("clientName");
        String key = payload.get("apiKey");
        
        if (name == null || key == null) return ResponseEntity.badRequest().build();
        
        License license = new License(key.trim(), name);
        return ResponseEntity.ok(repository.save(license));
    }

    // REAL VALIDATION ENDPOINT
    // Now validates keys against the database records.
    @RequestMapping(value = "/validate", method = {RequestMethod.GET, RequestMethod.POST})
    public String validate(
            @RequestHeader(value = "X-Monitoring-Api-Key", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam) {
        
        String apiKey = (apiKeyHeader != null) ? apiKeyHeader : apiKeyParam;
        System.out.println("🔍 [9090] Real Validation Request for Key: " + apiKey);

        if (apiKey != null) {
            // Trim whitespace to prevent validation mismatches
            String trimmedKey = apiKey.trim(); 
            
            // Search the database for a matching key
            Optional<License> licenseOpt = repository.findByApiKey(trimmedKey);
            
            // Return valid only if the key exists AND it is active
            if (licenseOpt.isPresent() && licenseOpt.get().isActive()) {
                return "{\"status\":\"valid\"}";
            }
        }

        return "{\"status\":\"invalid\"}";
    }
}
