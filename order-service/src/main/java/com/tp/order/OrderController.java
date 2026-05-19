package com.tp.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Order Service REST controller.
 * Exposes endpoints that delegate calls to other services through resilience-protected clients.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderController(InventoryClient inventoryClient, PaymentClient paymentClient) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    /**
     * Tests the Circuit Breaker via the Inventory Service.
     * GET /orders/check/{productId}
     */
    @GetMapping("/check/{productId}")
    public Map<String, String> checkOrder(@PathVariable String productId) {
        Map<String, String> response = new HashMap<>();
        response.put("productId", productId);
        response.put("inventoryResponse", inventoryClient.checkStock(productId));
        return response;
    }

    /**
     * Tests the Bulkhead via the Payment Service.
     * GET /orders/pay/{orderId}
     */
    @GetMapping("/pay/{orderId}")
    public Map<String, String> payOrder(@PathVariable String orderId) {
        Map<String, String> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("paymentResponse", paymentClient.processPayment(orderId));
        return response;
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "order-service");
        return response;
    }
}
