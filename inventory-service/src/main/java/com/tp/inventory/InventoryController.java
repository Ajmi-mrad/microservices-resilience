package com.tp.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

/**
 * Inventory Service.
 * Simulates random failures (60% probability) to demonstrate the Circuit Breaker.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final Random random = new Random();

    @GetMapping("/{productId}")
    public ResponseEntity<String> getStock(@PathVariable String productId) {
        // 60% chance of failure to trigger the Circuit Breaker
        if (random.nextInt(100) < 60) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Simulated failure for product " + productId);
        }
        int stock = 10 + random.nextInt(50);
        return ResponseEntity.ok("Product " + productId + " - Stock: " + stock);
    }
}
