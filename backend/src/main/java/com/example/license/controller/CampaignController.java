package com.example.license.controller;

import com.example.license.entity.Campaign;
import com.example.license.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    @Autowired
    private CampaignRepository campaignRepository;

    @GetMapping("/active")
    public ResponseEntity<List<Campaign>> getActiveCampaigns() {
        return ResponseEntity.ok(campaignRepository.findAll());
    }

    @PostMapping("/send")
    public ResponseEntity<String> triggerPush(@RequestBody Map<String, Object> pushRequest) {
        String title = (String) pushRequest.get("title");
        String message = (String) pushRequest.get("body");
        String imageUrl = (String) pushRequest.get("imageUrl");
        String targetPackage = (String) pushRequest.get("targetPackage");
        
        if (targetPackage == null || targetPackage.isEmpty()) {
            targetPackage = "com.realworld.app"; // Default fallback
        }
        
        System.out.println("🚀 [9090] Sending Campaign to [" + targetPackage + "]: " + title);
        
        try {
            // Escape single quotes for the shell command
            String safeTitle = (title != null ? title : "").replace("'", "'\\''");
            String safeMsg = (message != null ? message : "").replace("'", "'\\''");
            String safeImg = (imageUrl != null ? imageUrl : "").replace("'", "'\\''");

            // FORMAT: adb shell "am broadcast ... --es key 'value'"
            String broadcastCmd = String.format(
                "am broadcast -a com.realworld.app.TEST_PUSH -p %s --es title '%s' --es message '%s' --es imageUrl '%s'",
                targetPackage, safeTitle, safeMsg, safeImg
            );

            System.out.println("DEBUG: Executing -> adb shell \"" + broadcastCmd + "\"");
            
            ProcessBuilder pb = new ProcessBuilder("adb", "shell", broadcastCmd);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return ResponseEntity.ok("Campaign Sent Successfully!");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ADB Failed with code: " + exitCode);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}