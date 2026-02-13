package com.aws.quarkus.service;

import com.aws.quarkus.model.Device;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheKey;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

/**
 * Service that simulates async device processing with idempotency support
 * Ideal for testing async polling patterns with RestAssured
 */
@ApplicationScoped
public class AsyncDeviceService {

    private static final Logger LOGGER = Logger.getLogger(AsyncDeviceService.class);
    
    // In-memory storage (in real app, this would be AWS DynamoDB, SQS, etc.)
    private final Map<String, Device> deviceStorage = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    /**
     * Create device asynchronously with idempotency support
     */
    public Uni<Device> createDeviceAsync(Device device) {
        return Uni.createFrom().item(() -> {
            LOGGER.infof("Starting async creation of device: %s", device.getName());
            
            // Check idempotency
            String existingId = idempotencyCache.get(device.getIdempotencyKey());
            if (existingId != null) {
                LOGGER.infof("Idempotency key found, returning existing device: %s", existingId);
                return deviceStorage.get(existingId);
            }
            
            // Generate ID if not provided
            if (device.getId() == null || device.getId().isEmpty()) {
                device.setId(generateDeviceId());
            }
            
            // Set initial processing status
            device.setProcessingStatus(Device.ProcessingStatus.PROCESSING);
            
            // Store device and idempotency mapping
            deviceStorage.put(device.getId(), device);
            idempotencyCache.put(device.getIdempotencyKey(), device.getId());
            
            // Simulate async processing
            processDeviceAsync(device.getId());
            
            return device;
        });
    }

    /**
     * Get device by ID with caching
     */
    @CacheResult(cacheName = "device-cache")
    public Uni<Device> getDeviceById(@CacheKey String deviceId) {
        return Uni.createFrom().item(() -> {
            Device device = deviceStorage.get(deviceId);
            if (device == null) {
                throw new RuntimeException("Device not found: " + deviceId);
            }
            LOGGER.infof("Retrieved device: %s, status: %s", deviceId, device.getProcessingStatus());
            return device;
        });
    }

    /**
     * Update device asynchronously
     */
    public Uni<Device> updateDeviceAsync(String deviceId, Device updates) {
        return Uni.createFrom().item(() -> {
            Device existing = deviceStorage.get(deviceId);
            if (existing == null) {
                throw new RuntimeException("Device not found: " + deviceId);
            }
            
            // Update fields
            if (updates.getName() != null) {
                existing.setName(updates.getName());
            }
            if (updates.getData() != null) {
                existing.setData(updates.getData());
            }
            
            existing.setProcessingStatus(Device.ProcessingStatus.PROCESSING);
            
            // Simulate async update processing
            processDeviceAsync(deviceId);
            
            LOGGER.infof("Started async update for device: %s", deviceId);
            return existing;
        });
    }

    /**
     * Delete device asynchronously
     */
    public Uni<Boolean> deleteDeviceAsync(String deviceId) {
        return Uni.createFrom().item(() -> {
            Device device = deviceStorage.get(deviceId);
            if (device == null) {
                return false;
            }
            
            device.setProcessingStatus(Device.ProcessingStatus.PROCESSING);
            
            // Simulate async deletion
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                .execute(() -> {
                    deviceStorage.remove(deviceId);
                    LOGGER.infof("Device deleted: %s", deviceId);
                });
            
            return true;
        });
    }

    /**
     * Check if device exists
     */
    public boolean deviceExists(String deviceId) {
        return deviceStorage.containsKey(deviceId);
    }

    /**
     * Get all devices (for testing purposes)
     */
    public Map<String, Device> getAllDevices() {
        return Map.copyOf(deviceStorage);
    }

    /**
     * Clear all devices (for testing cleanup)
     */
    public void clearAllDevices() {
        deviceStorage.clear();
        idempotencyCache.clear();
        LOGGER.info("All devices cleared");
    }

    /**
     * Simulate async processing with random delays and occasional failures
     */
    private void processDeviceAsync(String deviceId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Random processing time between 1-5 seconds
                int delaySeconds = ThreadLocalRandom.current().nextInt(1, 6);
                Thread.sleep(delaySeconds * 1000L);
                
                Device device = deviceStorage.get(deviceId);
                if (device != null) {
                    // 10% chance of failure to test error scenarios
                    if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                        device.setProcessingStatus(Device.ProcessingStatus.FAILED);
                        LOGGER.warnf("Device processing failed: %s", deviceId);
                    } else {
                        device.setProcessingStatus(Device.ProcessingStatus.COMPLETED);
                        LOGGER.infof("Device processing completed: %s", deviceId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Device device = deviceStorage.get(deviceId);
                if (device != null) {
                    device.setProcessingStatus(Device.ProcessingStatus.FAILED);
                }
                LOGGER.errorf("Device processing interrupted: %s", deviceId);
            }
        });
    }

    private String generateDeviceId() {
        return "device-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
