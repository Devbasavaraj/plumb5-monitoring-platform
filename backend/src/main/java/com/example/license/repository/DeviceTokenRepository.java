package com.example.license.repository;

import com.example.license.entity.DeviceToken;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByLicenseAndActiveTrue(License license);

    List<DeviceToken> findByLicenseAndUserEmailIgnoreCaseAndActiveTrue(License license, String userEmail);

    long countByLicenseAndActiveTrue(License license);
}
