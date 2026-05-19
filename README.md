# Microservices Resilience TP

A simple Spring Boot project implementing **Circuit Breaker** and **Bulkhead** patterns with Resilience4j.

## Architecture

```
Client
   |
   v
Order Service (8080)  --[Circuit Breaker]--> Inventory Service (8081)
                      --[Bulkhead]----------> Payment Service (8082)
```

| Service           | Port | Role                                                |
|-------------------|------|-----------------------------------------------------|
| Order Service     | 8080 | Consumer with Circuit Breaker + Bulkhead            |
| Inventory Service | 8081 | Simulates random failures (60%) - tests CB          |
| Payment Service   | 8082 | Slow service (3s response) - tests Bulkhead         |

## Prerequisites

- Java 17+
- Maven 3.6+

## Build

From the project root:

```bash
mvn clean install
```

## Run (3 terminals)

**Terminal 1 - Inventory Service:**
```bash
cd inventory-service
mvn spring-boot:run
```

**Terminal 2 - Payment Service:**
```bash
cd payment-service
mvn spring-boot:run
```

**Terminal 3 - Order Service:**
```bash
cd order-service
mvn spring-boot:run
```

## Test endpoints

- Check stock (Circuit Breaker): `GET http://localhost:8080/orders/check/P1`
- Pay order (Bulkhead): `GET http://localhost:8080/orders/pay/ORDER1`
- Health: `GET http://localhost:8080/actuator/health`
- Circuit Breaker state: `GET http://localhost:8080/actuator/circuitbreakers`
- Bulkhead state: `GET http://localhost:8080/actuator/bulkheads`

## Run automated tests

```powershell
.\test.ps1
```

## Resilience configuration

**Circuit Breaker** (`inventoryService`):
- Sliding window: 10 calls
- Failure threshold: 50%
- Wait in OPEN state: 10 seconds

**Bulkhead** (`paymentService`):
- Max concurrent calls: 2
- Max wait duration: 0 (immediate reject)
