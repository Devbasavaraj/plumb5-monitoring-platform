package com.example.license.repository;

import com.example.license.entity.Geofence;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GeofenceRepository extends JpaRepository<Geofence, Long> {
    List<Geofence> findByLicense(License license);
}
