package com.example.license.repository;

import com.example.license.entity.InAppMessage;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InAppMessageRepository extends JpaRepository<InAppMessage, Long> {
    List<InAppMessage> findByLicenseAndActiveTrueOrderByIdDesc(License license);

    List<InAppMessage> findByLicenseOrderByIdDesc(License license);
}
