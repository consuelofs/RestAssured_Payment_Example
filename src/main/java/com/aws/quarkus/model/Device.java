package com.aws.quarkus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Device model for testing async/idempotent operations
 * Compatible with Java 21 and Quarkus 3.15.1
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String id;
    
    private String name;
    
    private Map<String, String> data;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("updated_at")  
    private Instant updatedAt;
    
    @JsonProperty("processing_status")
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;
    
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    public Device() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public Device(String id, String name, Map<String, String> data) {
        this();
        this.id = id;
        this.name = name;
        this.data = data;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { 
        this.data = data;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { 
        this.processingStatus = processingStatus;
        this.updatedAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public boolean isProcessingComplete() {
        return processingStatus == ProcessingStatus.COMPLETED || 
               processingStatus == ProcessingStatus.FAILED;
    }

    public void print() {
        System.out.printf("""
            Device Info:
            - ID: %s
            - Name: %s
            - Data: %s
            - Status: %s
            - Idempotency Key: %s
            - Created: %s
            - Updated: %s
            """, id, name, data, processingStatus, idempotencyKey, createdAt, updatedAt);
    }

    /**
     * Enum for async processing status
     */
    public enum ProcessingStatus {
        PENDING,
        PROCESSING, 
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
