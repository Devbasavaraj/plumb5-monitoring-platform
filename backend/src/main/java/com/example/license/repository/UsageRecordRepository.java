package com.example.license.repository;

import com.example.license.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
}
