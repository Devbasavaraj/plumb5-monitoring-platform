package com.example.license.controller;

import com.example.license.entity.Beacon;
import com.example.license.entity.Geofence;
import com.example.license.entity.License;
import com.example.license.repository.BeaconRepository;
import com.example.license.repository.GeofenceRepository;
import com.example.license.repository.LicenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private GeofenceRepository geofenceRepository;

    @Autowired
    private BeaconRepository beaconRepository;

    @PostMapping("/geofence/add")
    public ResponseEntity<Geofence> addGeofence(@RequestBody Map<String, Object> payload) {
        String apiKey = (String) payload.get("apiKey");
        Optional<License> licenseOpt = licenseRepository.findByApiKey(apiKey);
        
        if (licenseOpt.isPresent()) {
            Geofence gf = new Geofence();
            gf.setGeofenceId((String) payload.get("geofenceId"));
            gf.setName((String) payload.get("name"));
            gf.setLatitude(Double.parseDouble(payload.get("lat").toString()));
            gf.setLongitude(Double.parseDouble(payload.get("lon").toString()));
            gf.setRadius(Float.parseFloat(payload.get("radius").toString()));
            gf.setLicense(licenseOpt.get());
            return ResponseEntity.ok(geofenceRepository.save(gf));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/beacon/add")
    public ResponseEntity<Beacon> addBeacon(@RequestBody Map<String, Object> payload) {
        String apiKey = (String) payload.get("apiKey");
        Optional<License> licenseOpt = licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isPresent()) {
            Beacon b = new Beacon();
            b.setBeaconUuid((String) payload.get("uuid"));
            b.setMajor(Integer.parseInt(payload.get("major").toString()));
            b.setMinor(Integer.parseInt(payload.get("minor").toString()));
            b.setLicense(licenseOpt.get());
            return ResponseEntity.ok(beaconRepository.save(b));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/triggers/all")
    public ResponseEntity<Map<String, Object>> getAllTriggers() {
        Map<String, Object> response = new HashMap<>();
        response.put("geofences", geofenceRepository.findAll());
        response.put("beacons", beaconRepository.findAll());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/trigger/remove")
    public ResponseEntity<Void> removeTrigger(@RequestParam String type, @RequestParam Long id) {
        if ("geofence".equalsIgnoreCase(type)) {
            geofenceRepository.deleteById(id);
        } else {
            beaconRepository.deleteById(id);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncConfig(@RequestHeader("X-Monitoring-Api-Key") String apiKey) {
        Optional<License> licenseOpt = licenseRepository.findByApiKey(apiKey);

        if (licenseOpt.isEmpty() || !licenseOpt.get().isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        License license = licenseOpt.get();
        List<Geofence> geofences = geofenceRepository.findByLicense(license);
        List<Beacon> beacons = beaconRepository.findByLicense(license);

        Map<String, Object> response = new HashMap<>();
        
        List<Map<String, Object>> gfList = geofences.stream().map(gf -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", gf.getGeofenceId());
            m.put("lat", gf.getLatitude());
            m.put("lon", gf.getLongitude());
            m.put("radius", gf.getRadius());
            return m;
        }).collect(Collectors.toList());


List<String> bList = beacons.stream()
        .map(Beacon::getBeaconUuid)
        .collect(Collectors.toList());

// Include UUID, major and minor for SDK beacon matching.
List<Map<String, Object>> beaconFilterList = beacons.stream()
        .map(beacon -> {
            Map<String, Object> item = new HashMap<>();
            item.put("uuid", beacon.getBeaconUuid());
            item.put("major", beacon.getMajor());
            item.put("minor", beacon.getMinor());
            return item;
        })
        .collect(Collectors.toList());

response.put("geofences", gfList);

// Keep the existing field unchanged for older SDK clients.
response.put("beacons", bList);

// New field for SDK versions supporting iBeacon filters.
response.put("beaconFilters", beaconFilterList);

        
        // Dynamic SDK Settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("batchSize", 50);
        settings.put("syncIntervalMinutes", 15);
        response.put("sdkSettings", settings);

        return ResponseEntity.ok(response);
    }
}
