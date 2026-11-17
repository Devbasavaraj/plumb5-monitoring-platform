package com.example.license.repository;



import org.springframework.data.jpa.repository.JpaRepository;

import com.example.license.entity.License;

import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
	Optional<License> findByApiKey(String apiKey);
}