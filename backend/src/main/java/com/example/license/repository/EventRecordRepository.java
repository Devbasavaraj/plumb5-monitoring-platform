package com.example.license.repository;

import com.example.license.entity.EventRecord;
import com.example.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {
    List<EventRecord> findByLicense(License license);
}
