package com.tp.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment Service.
 * Intentionally slow processing (3 seconds) to demonstrate Bulkhead behaviour:
 * if too many parallel calls arrive, the bulkhead rejects the excess.
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {

    @GetMapping("/{orderId}")
    public String processPayment(@PathVariable String orderId) throws InterruptedException {
        // Simulated slow processing: 3 seconds
        Thread.sleep(3000);
        return "Payment processed for order " + orderId;
    }
}
