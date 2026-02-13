package com.aws.quarkus.resource;

import com.aws.quarkus.model.PaymentOrder;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST API para pagos v1 con control de rate limiting
 * Diseñado para testing de backpressure y carga
 */
@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentV1Resource {

    private static final AtomicInteger requestCounter = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_REQUESTS = 8; // Para simular backpressure

    @POST
    @Path("/{orderId}")
    public Response processPayment(@PathParam("orderId") String orderId, PaymentOrder paymentData) {
        int currentRequests = requestCounter.incrementAndGet();
        
        try {
            // Simular backpressure: si hay demasiadas peticiones, rechazar con 429
            if (currentRequests > MAX_CONCURRENT_REQUESTS) {
                return Response.status(429) // Too Many Requests
                    .entity(Map.of(
                        "message", "Rate limit exceeded. Please try again later.",
                        "retryAfter", 5,
                        "currentRequests", currentRequests
                    ))
                    .build();
            }

            // Simular procesamiento
            String jobId = "job-v1-" + UUID.randomUUID().toString().substring(0, 8);
            
            Map<String, Object> response = Map.of(
                "orderId", orderId,
                "jobId", jobId,
                "status", "ACCEPTED",
                "message", "Payment processing initiated",
                "timestamp", Instant.now()
            );

            return Response.status(Response.Status.ACCEPTED).entity(response).build();

        } finally {
            // Liberar el contador después de un pequeño delay
            new Thread(() -> {
                try {
                    Thread.sleep(100); // Simular tiempo de procesamiento mínimo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                requestCounter.decrementAndGet();
            }).start();
        }
    }

    @GET
    @Path("/_metrics")
    public Response getMetrics() {
        return Response.ok(Map.of(
            "currentRequests", requestCounter.get(),
            "maxConcurrentRequests", MAX_CONCURRENT_REQUESTS
        )).build();
    }
}
