package com.plumb5.monitor.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    void insertTransaction(TransactionEntity transaction);

    @Insert
    void insertTransactions(List<TransactionEntity> transactions);

    @Query("SELECT * FROM monitoring_transactions ORDER BY timestamp ASC LIMIT :limit")
    List<TransactionEntity> getPendingTransactions(int limit);

    @Query("SELECT COUNT(*) FROM monitoring_transactions")
    int getTransactionCount();

    @Delete
    void deleteTransactions(List<TransactionEntity> transactions);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("DELETE FROM monitoring_transactions")
    void deleteAll();

    @Query("DELETE FROM monitoring_transactions WHERE timestamp < :threshold")
    void pruneOldTransactions(long threshold);

    @Query("DELETE FROM monitoring_transactions WHERE id IN " +
            "(SELECT id FROM monitoring_transactions " +
            "ORDER BY timestamp ASC LIMIT :limit)")
    void deleteOldest(int limit);
}