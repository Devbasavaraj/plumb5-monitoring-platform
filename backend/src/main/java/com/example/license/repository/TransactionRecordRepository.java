package com.example.license.repository;

import com.example.license.entity.License;
import com.example.license.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findByLicense(License license);
}
