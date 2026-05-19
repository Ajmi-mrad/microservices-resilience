package com.tp.order;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Client for the Payment Service.
 * Protected by a Bulkhead (Resilience4j).
 *
 * The Bulkhead limits the number of concurrent calls,
 * preventing one slow service from monopolising all available threads.
 */
@Service
public class PaymentClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment.url}")
    private String paymentUrl;

    public PaymentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls the Payment Service.
     * Type SEMAPHORE: maxConcurrentCalls limits parallel executions.
     * If full, the call is immediately rejected with BulkheadFullException.
     */
    @Bulkhead(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public String processPayment(String orderId) {
        String url = paymentUrl + "/payment/" + orderId;
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fallback invoked when the Bulkhead is full.
     */
    @SuppressWarnings("unused")
    private String processPaymentFallback(String orderId, Throwable t) {
        return "REJECTED: Bulkhead full - payment service overloaded for order " + orderId;
    }
}
