package com.example.license.repository;

import com.example.license.entity.Beacon;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BeaconRepository extends JpaRepository<Beacon, Long> {
    List<Beacon> findByLicense(License license);
}
