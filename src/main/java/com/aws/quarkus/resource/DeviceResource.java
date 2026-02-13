package com.aws.quarkus.resource;

import com.aws.quarkus.model.Device;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * REST API for async device operations
 * Compatible with Java 21 and designed for async/idempotent testing
 */
@Path("/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {

    // In-memory storage for demo (use AWS DynamoDB in production)
    private static final Map<String, Device> deviceStorage = new ConcurrentHashMap<>();
    private static final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GET
    public Response getAllDevices() {
        return Response.ok(deviceStorage.values()).build();
    }

    @GET
    @Path("/{id}")
    public Response getDevice(@PathParam("id") String deviceId) {
        Device device = deviceStorage.get(deviceId);
        if (device == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Device not found: " + deviceId))
                .build();
        }
        return Response.ok(device).build();
    }

    @POST
    public Response createDevice(Device device) {
        // Handle idempotency
        if (device.getIdempotencyKey() != null) {
            String existingId = idempotencyCache.get(device.getIdempotencyKey());
            if (existingId != null) {
                Device existingDevice = deviceStorage.get(existingId);
                return Response.ok(existingDevice).build();
            }
        }

        // Generate ID if not provided
        if (device.getId() == null || device.getId().isEmpty()) {
            device.setId(generateDeviceId());
        }

        // Set initial processing status
        device.setProcessingStatus(Device.ProcessingStatus.PROCESSING);
        
        // Store device and idempotency mapping
        deviceStorage.put(device.getId(), device);
        if (device.getIdempotencyKey() != null) {
            idempotencyCache.put(device.getIdempotencyKey(), device.getId());
        }

        // Start async processing
        processDeviceAsync(device.getId());

        return Response.status(Response.Status.ACCEPTED).entity(device).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateDevice(@PathParam("id") String deviceId, Device updates) {
        Device existing = deviceStorage.get(deviceId);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Device not found: " + deviceId))
                .build();
        }

        // Update fields
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getData() != null) {
            existing.setData(updates.getData());
        }

        existing.setProcessingStatus(Device.ProcessingStatus.PROCESSING);

        // Start async processing
        processDeviceAsync(deviceId);

        return Response.status(Response.Status.ACCEPTED).entity(existing).build();
    }

    @PATCH
    @Path("/{id}")
    public Response partialUpdateDevice(@PathParam("id") String deviceId, Map<String, Object> updates) {
        Device device = deviceStorage.get(deviceId);
        if (device == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Device not found: " + deviceId))
                .build();
        }

        // Apply partial updates
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> device.setName(value.toString());
                case "data" -> {
                    if (value instanceof Map<?, ?> mapValue) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> dataMap = (Map<String, String>) mapValue;
                        device.setData(dataMap);
                    }
                }
            }
        });

        device.setProcessingStatus(Device.ProcessingStatus.PROCESSING);
        processDeviceAsync(deviceId);

        return Response.status(Response.Status.ACCEPTED).entity(device).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteDevice(@PathParam("id") String deviceId) {
        Device device = deviceStorage.get(deviceId);
        if (device == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Device not found: " + deviceId))
                .build();
        }

        device.setProcessingStatus(Device.ProcessingStatus.PROCESSING);

        // Simulate async deletion
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
            .execute(() -> {
                deviceStorage.remove(deviceId);
                System.out.printf("Device deleted: %s%n", deviceId);
            });

        return Response.ok(Map.of("message", "Device deletion initiated for id = " + deviceId)).build();
    }

    @GET
    @Path("/{id}/status")
    public Response getDeviceStatus(@PathParam("id") String deviceId) {
        Device device = deviceStorage.get(deviceId);
        if (device == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Device not found: " + deviceId))
                .build();
        }

        Map<String, Object> status = Map.of(
            "id", device.getId(),
            "status", device.getProcessingStatus(),
            "isComplete", device.isProcessingComplete(),
            "lastUpdated", device.getUpdatedAt()
        );

        return Response.ok(status).build();
    }

    @DELETE
    @Path("/_test/cleanup")
    public Response cleanupForTesting() {
        deviceStorage.clear();
        idempotencyCache.clear();
        return Response.ok(Map.of("message", "All devices cleared")).build();
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
                        System.out.printf("Device processing failed: %s%n", deviceId);
                    } else {
                        device.setProcessingStatus(Device.ProcessingStatus.COMPLETED);
                        System.out.printf("Device processing completed: %s%n", deviceId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Device device = deviceStorage.get(deviceId);
                if (device != null) {
                    device.setProcessingStatus(Device.ProcessingStatus.FAILED);
                }
                System.out.printf("Device processing interrupted: %s%n", deviceId);
            }
        });
    }

    private String generateDeviceId() {
        return "device-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
