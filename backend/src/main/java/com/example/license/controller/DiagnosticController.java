package com.example.license.controller;

import com.example.license.entity.License;
import com.example.license.repository.LicenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticController {

    @Autowired
    private LicenseRepository licenseRepository;

    private static final String LOGS_DIR = "sdk_logs";

    @PostMapping("/logs")
    public ResponseEntity<String> uploadLogs(
            @RequestHeader("X-Monitoring-Api-Key") String apiKey,
            @RequestParam("file") MultipartFile file) {

        Optional<License> licenseOpt = licenseRepository.findByApiKey(apiKey);
        if (licenseOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Path root = Paths.get(LOGS_DIR);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            String filename = licenseOpt.get().getApiKey() + "_" + System.currentTimeMillis() + ".txt";
            Files.copy(file.getInputStream(), root.resolve(filename));

            return ResponseEntity.ok("Logs received: " + filename);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save logs");
        }
    }
}
