package com.aws.quarkus.resource;

import com.aws.quarkus.model.PaymentOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * REST API para órdenes de pago
 * Diseñado para testing de integración con RestAssured
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentOrderResource {

    // In-memory storage para demo (usar base de datos en producción)
    private static final Map<String, PaymentOrder> orderStorage = new ConcurrentHashMap<>();
    private static final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GET
    public Response getAllOrders() {
        return Response.ok(orderStorage.values()).build();
    }

    @GET
    @Path("/{orderId}")
    public Response getOrder(@PathParam("orderId") String orderId) {
        PaymentOrder order = orderStorage.get(orderId);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Order not found: " + orderId))
                .build();
        }
        return Response.ok(order).build();
    }

    @GET
    @Path("/{orderId}/status")
    public Response getOrderStatus(@PathParam("orderId") String orderId) {
        PaymentOrder order = orderStorage.get(orderId);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("message", "Order not found: " + orderId))
                .build();
        }

        Map<String, Object> status = Map.of(
            "orderId", order.getOrderId(),
            "jobId", order.getJobId() != null ? order.getJobId() : "",
            "status", order.getStatus(),
            "processedAt", order.getProcessedAt(),
            "paymentResult", order.isProcessingComplete() ? 
                Map.of("status", "SUCCESS", "transactionId", "txn-" + UUID.randomUUID().toString().substring(0, 12)) :
                Map.of("status", "PENDING")
        );

        return Response.ok(status).build();
    }

    @POST
    public Response createOrder(PaymentOrder order) {
        // Validaciones básicas
        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", "orderId is required"))
                .build();
        }

        // Manejo de idempotencia
        if (order.getIdempotencyKey() != null) {
            String existingJobId = idempotencyCache.get(order.getIdempotencyKey());
            if (existingJobId != null) {
                PaymentOrder existingOrder = orderStorage.get(order.getOrderId());
                if (existingOrder != null) {
                    return Response.status(Response.Status.ACCEPTED).entity(existingOrder).build();
                }
            }
        }

        // Verificar si la orden ya existe (usando orderId como clave de idempotencia)
        PaymentOrder existingOrder = orderStorage.get(order.getOrderId());
        if (existingOrder != null) {
            return Response.status(Response.Status.ACCEPTED).entity(existingOrder).build();
        }

        // Generar jobId único
        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 12);
        order.setJobId(jobId);
        order.setStatus(PaymentOrder.OrderStatus.ACCEPTED);

        if (order.getTimestamp() == null) {
            order.setTimestamp(Instant.now());
        }

        // Almacenar orden y mapeo de idempotencia
        orderStorage.put(order.getOrderId(), order);
        if (order.getIdempotencyKey() != null) {
            idempotencyCache.put(order.getIdempotencyKey(), jobId);
        }

        // Iniciar procesamiento asíncrono
        processOrderAsync(order.getOrderId());

        // Respuesta inmediata con status 202 Accepted
        return Response.status(Response.Status.ACCEPTED).entity(order).build();
    }

    @DELETE
    @Path("/_test/cleanup")
    public Response cleanupForTesting() {
        orderStorage.clear();
        idempotencyCache.clear();
        return Response.ok(Map.of("message", "All orders cleared")).build();
    }

    /**
     * Simular procesamiento asíncrono con delays aleatorios
     */
    private void processOrderAsync(String orderId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Tiempo de procesamiento aleatorio entre 2-8 segundos
                int delaySeconds = ThreadLocalRandom.current().nextInt(2, 9);
                Thread.sleep(delaySeconds * 1000L);

                PaymentOrder order = orderStorage.get(orderId);
                if (order != null) {
                    // 5% chance de falla para simular errores reales
                    if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                        order.setStatus(PaymentOrder.OrderStatus.FAILED);
                        System.out.printf("Payment order processing failed: %s%n", orderId);
                    } else {
                        order.setStatus(PaymentOrder.OrderStatus.COMPLETED);
                        order.setProcessedAt(Instant.now());
                        System.out.printf("Payment order processing completed: %s%n", orderId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                PaymentOrder order = orderStorage.get(orderId);
                if (order != null) {
                    order.setStatus(PaymentOrder.OrderStatus.FAILED);
                }
                System.out.printf("Payment order processing interrupted: %s%n", orderId);
            }
        });
    }
}
