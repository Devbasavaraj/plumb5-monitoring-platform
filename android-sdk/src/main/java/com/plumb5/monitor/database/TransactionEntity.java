package com.plumb5.monitor.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(
        tableName = "monitoring_transactions",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"appId"}),
                @Index(value = {"sessionId"})
        }
)
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @SerializedName("transactionId")
    private String transactionId = "";

    @SerializedName("appId")
    private String appId;

    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("userId")
    private String userId;

    @SerializedName("timestamp")
    private long timestamp;

    /**
     * Original transaction data received from the application.
     * Stored as JSON string locally.
     */
    @SerializedName("transactionData")
    private String transactionData;

    /**
     * Number of upload attempts.
     */
    @SerializedName("retryCount")
    private int retryCount;

    public TransactionEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(@NonNull String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTransactionData() {
        return transactionData;
    }

    public void setTransactionData(String transactionData) {
        this.transactionData = transactionData;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}