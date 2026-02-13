package com.aws.quarkus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Modelo de orden de pago para testing de integración
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentOrder {
    
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("jobId")
    private String jobId;
    
    @JsonProperty("customerEmail")
    private String customerEmail;
    
    @JsonProperty("amount")
    private double amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    
    @JsonProperty("status")
    private OrderStatus status;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("processedAt")
    private Instant processedAt;
    
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    public enum OrderStatus {
        PENDING, ACCEPTED, PROCESSING, COMPLETED, FAILED
    }

    // Constructors
    public PaymentOrder() {}

    public PaymentOrder(String orderId, String customerEmail, double amount) {
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.timestamp = Instant.now();
    }

    // Getters and setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public boolean isProcessingComplete() {
        return status == OrderStatus.COMPLETED || status == OrderStatus.FAILED;
    }
}
