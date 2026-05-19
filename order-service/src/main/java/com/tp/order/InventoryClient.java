package com.tp.order;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Client for the Inventory Service.
 * Protected by a Circuit Breaker (Resilience4j).
 *
 * If the Inventory Service fails repeatedly, the circuit opens
 * and calls are immediately rejected (Fast-Fail), avoiding error propagation.
 */
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    @Value("${services.inventory.url}")
    private String inventoryUrl;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls the Inventory Service to check availability.
     * The "inventoryService" name matches the configuration in application.yml.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "checkStockFallback")
    public String checkStock(String productId) {
        String url = inventoryUrl + "/inventory/" + productId;
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fallback method invoked when the circuit is OPEN
     * or when an exception is thrown by checkStock().
     * The signature must match the original + a Throwable parameter.
     */
    @SuppressWarnings("unused")
    private String checkStockFallback(String productId, Throwable t) {
        return "FALLBACK: Inventory service unavailable for product " + productId
                + " (reason: " + t.getClass().getSimpleName() + ")";
    }
}
