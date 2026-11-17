package com.example.license.repository;

import com.example.license.entity.Campaign;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByLicenseAndActiveTrue(License license);
}
