# Microservices Résilients — Résumé Complet du Projet

> **Pour qui ?** Ce document explique le projet de zéro. Aucune connaissance préalable n'est requise.

---

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Le problème — Pourquoi la résilience ?](#2-le-problème--pourquoi-la-résilience-)
3. [Les patterns implémentés](#3-les-patterns-implémentés)
4. [Architecture](#4-architecture)
5. [Stack technique](#5-stack-technique)
6. [Structure du projet](#6-structure-du-projet)
7. [Les services en détail](#7-les-services-en-détail)
8. [Prérequis et installation](#8-prérequis-et-installation)
9. [Compilation et démarrage](#9-compilation-et-démarrage)
10. [Guide de test](#10-guide-de-test)
11. [Référence de configuration](#11-référence-de-configuration)
12. [Correctifs appliqués](#12-correctifs-appliqués)

---

## 1. Vue d'ensemble

Ce projet est une **démonstration pratique** de deux patterns de résilience dans une architecture microservices Spring Boot :

- Le **Circuit Breaker** — qui protège le système contre les pannes en cascade
- Le **Bulkhead** — qui protège les ressources contre la surcharge par des services lents

Concrètement, trois services Spring Boot communiquent entre eux via HTTP. Le service principal (`order-service`) appelle les deux autres. Ces deux services sont **volontairement défaillants** (l'un tombe en panne aléatoirement, l'autre répond lentement) pour forcer le déclenchement des mécanismes de résilience.

L'objectif pédagogique : montrer qu'un système bien conçu peut **survivre aux pannes des services dont il dépend**, plutôt que de tomber lui-même en cascade.

---

## 2. Le problème — Pourquoi la résilience ?

### Le problème des pannes en cascade

Imaginez une chaîne de restaurants. Le restaurant A commande ses légumes au fournisseur B. Si B tombe en panne et que A continue d'envoyer des commandes sans résultat, A va se retrouver bloqué à attendre — et ses propres clients attendront aussi. La panne de B a provoqué la panne de A.

Dans les microservices, c'est pareil : si le service `inventory-service` commence à renvoyer des erreurs, et que `order-service` continue d'appeler `inventory-service` sans protection, `order-service` va :

1. Accumuler des threads en attente de réponse
2. Épuiser ses ressources
3. Tomber à son tour en panne

C'est ce qu'on appelle une **panne en cascade** (*cascading failure*).

### Le problème des services lents

Un service lent est parfois pire qu'un service en panne. Si `payment-service` met 3 secondes pour répondre, et que 100 utilisateurs envoient une requête simultanément, `order-service` va créer 100 threads en attente. Ces threads consomment de la mémoire et du CPU. Au bout d'un moment, il n'y a plus de threads disponibles pour traiter d'autres requêtes — même celles qui n'ont rien à voir avec les paiements.

---

## 3. Les patterns implémentés

### 3.1 Circuit Breaker (Disjoncteur)

**Analogie :** Un disjoncteur électrique dans un tableau. Quand le courant est trop fort (trop d'erreurs), il coupe le circuit pour protéger l'installation. Après un certain temps, il se remet en position de test pour voir si le problème est résolu.

Le Circuit Breaker a **trois états** :

```
        trop d'échecs                 timeout écoulé
  CLOSED ──────────────► OPEN ──────────────────────► HALF_OPEN
    ▲                                                      │
    │          tests réussis (service rétabli)             │
    └──────────────────────────────────────────────────────┘
          tests échoués → retour à OPEN
```

| État | Comportement | Description |
|------|-------------|-------------|
| `CLOSED` | Appels normaux | Tout fonctionne, les appels passent vers le service distant |
| `OPEN` | Fast-fail immédiat | Trop d'échecs détectés — les appels sont rejetés SANS contacter le service (le fallback est déclenché) |
| `HALF_OPEN` | Test de récupération | Après le délai d'attente, quelques appels de test sont autorisés pour vérifier si le service est rétabli |

**Dans ce projet :** Le Circuit Breaker surveille les appels vers `inventory-service`. Dès que 50% des 5 derniers appels échouent, le circuit s'ouvre.

---

### 3.2 Bulkhead (Cloison étanche)

**Analogie :** Les cloisons étanches d'un navire. Si un compartiment est inondé, les cloisons empêchent l'eau de se propager aux autres compartiments. Le navire reste à flot même avec un compartiment noyé.

Le Bulkhead **limite le nombre d'appels simultanés** vers un service donné. Si la limite est atteinte, les nouveaux appels sont rejetés immédiatement (fallback) plutôt que d'attendre et de bloquer des threads.

**Dans ce projet :** Maximum 2 appels simultanés vers `payment-service`. Le 3ème appel simultané est rejeté immédiatement.

---

### 3.3 Fallback (Réponse dégradée)

Quand un circuit est ouvert ou qu'un bulkhead est plein, plutôt que de renvoyer une erreur brute au client, le système renvoie une **réponse dégradée mais contrôlée**.

Exemples dans ce projet :
- Circuit Breaker → `"FALLBACK: Inventory service unavailable for product P1"`
- Bulkhead → `"REJECTED: Bulkhead full - payment service overloaded for order ORDER1"`

Le client reçoit un message clair plutôt qu'une exception `500 Internal Server Error`.

---

## 4. Architecture

```
                              ┌─────────────────────────┐
                              │      Client              │
                              │  (navigateur / curl)     │
                              └────────────┬─────────────┘
                                           │ HTTP
                                           ▼
                              ┌─────────────────────────┐
                              │      Order Service       │
                              │        Port 8080         │
                              │   (service consommateur) │
                              └────────────┬─────────────┘
                                           │
                   ┌───────────────────────┼───────────────────────┐
                   │                                               │
         [Circuit Breaker]                               [Bulkhead]
                   │                                               │
                   ▼                                               ▼
    ┌──────────────────────────┐              ┌──────────────────────────┐
    │    Inventory Service     │              │     Payment Service       │
    │       Port 8081          │              │        Port 8082          │
    │  (60% d'échecs aléat.)   │              │   (réponse lente : 3s)   │
    └──────────────────────────┘              └──────────────────────────┘
```

### Tableau des services

| Service | Port | Rôle | Pattern appliqué |
|---------|------|------|-----------------|
| `order-service` | 8080 | Consommateur — reçoit les requêtes client et appelle les deux autres services | Applique CB + Bulkhead |
| `inventory-service` | 8081 | Fournisseur — simule des pannes aléatoires (60% d'erreurs) | Cible du Circuit Breaker |
| `payment-service` | 8082 | Fournisseur — simule un traitement lent (3 secondes par requête) | Cible du Bulkhead |

---

## 5. Stack technique

| Technologie | Version | Rôle |
|------------|---------|------|
| Java | 17 | Langage de développement |
| Spring Boot | 3.2.0 | Framework applicatif (serveur web, injection de dépendances) |
| Resilience4j | 2.2.0 | Bibliothèque de résilience (CB, Bulkhead, Fallback) |
| Spring Boot Actuator | 3.2.0 | Exposition des métriques et états de santé en temps réel |
| Spring AOP | 3.2.0 | Requis par Resilience4j pour intercepter les appels via annotations |
| Maven | 3.8+ | Gestionnaire de build (projet multi-modules) |
| RestTemplate | Spring 6 | Client HTTP pour les appels inter-services |

---

## 6. Structure du projet

```
microservices-resilience/
│
├── pom.xml                          ← POM parent (gère les 3 modules)
├── README.md                        ← Documentation rapide
├── RESUME.md                        ← Ce fichier
├── test.ps1                         ← Script PowerShell de test automatisé
│
├── order-service/                   ← SERVICE PRINCIPAL (port 8080)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/tp/order/
│       │   ├── OrderServiceApplication.java   ← Point d'entrée Spring Boot
│       │   ├── OrderController.java           ← Endpoints REST (/orders/...)
│       │   ├── InventoryClient.java           ← Appel CB vers inventory-service
│       │   └── PaymentClient.java             ← Appel Bulkhead vers payment-service
│       └── resources/
│           └── application.yml                ← Config CB + Bulkhead + Actuator
│
├── inventory-service/               ← SERVICE INVENTAIRE (port 8081)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/tp/inventory/
│       │   ├── InventoryServiceApplication.java
│       │   └── InventoryController.java        ← GET /inventory/{id} (60% erreurs)
│       └── resources/
│           └── application.yml
│
└── payment-service/                 ← SERVICE PAIEMENT (port 8082)
    ├── pom.xml
    └── src/main/
        ├── java/com/tp/payment/
        │   ├── PaymentServiceApplication.java
        │   └── PaymentController.java          ← GET /payment/{id} (sleep 3s)
        └── resources/
            └── application.yml
```

---

## 7. Les services en détail

### 7.1 Order Service (port 8080)

C'est le **service central** du projet. Il ne fait rien de métier par lui-même — il reçoit les requêtes du client et les délègue aux deux autres services en les protégeant avec les patterns de résilience.

#### Endpoints exposés

| Méthode | URL | Description |
|---------|-----|-------------|
| `GET` | `/orders/check/{productId}` | Vérifie le stock d'un produit via Circuit Breaker |
| `GET` | `/orders/pay/{orderId}` | Effectue un paiement via Bulkhead |
| `GET` | `/orders/health` | Health check simple |
| `GET` | `/actuator/health` | État de santé complet (CB inclus) |
| `GET` | `/actuator/circuitbreakers` | État en temps réel du Circuit Breaker |
| `GET` | `/actuator/bulkheads` | État en temps réel du Bulkhead |

#### InventoryClient — Circuit Breaker

```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "checkStockFallback")
public String checkStock(String productId) {
    // Appel HTTP vers inventory-service
    return restTemplate.getForObject(inventoryUrl + "/inventory/" + productId, String.class);
}

private String checkStockFallback(String productId, Throwable t) {
    return "FALLBACK: Inventory service unavailable for product " + productId
           + " (reason: " + t.getClass().getSimpleName() + ")";
}
```

- Si `inventory-service` répond avec une erreur → la méthode `checkStockFallback` est appelée
- Si le circuit est OPEN → `checkStockFallback` est appelée directement **sans** appeler `inventory-service` (fast-fail)

#### PaymentClient — Bulkhead

```java
@Bulkhead(name = "paymentService", fallbackMethod = "processPaymentFallback")
public String processPayment(String orderId) {
    // Appel HTTP vers payment-service (prend 3 secondes)
    return restTemplate.getForObject(paymentUrl + "/payment/" + orderId, String.class);
}

private String processPaymentFallback(String orderId, Throwable t) {
    return "REJECTED: Bulkhead full - payment service overloaded for order " + orderId;
}
```

- Si 2 appels sont déjà en cours → le 3ème est rejeté immédiatement et `processPaymentFallback` est appelée

---

### 7.2 Inventory Service (port 8081)

Service volontairement instable. Il simule un service réel qui aurait des problèmes intermittents.

#### Endpoint

`GET /inventory/{productId}`

#### Comportement

```java
if (random.nextInt(100) < 60) {
    // 60% de chance : retourne une erreur 500
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Simulated failure for product " + productId);
}
// 40% de chance : retourne un stock aléatoire entre 10 et 59
int stock = 10 + random.nextInt(50);
return ResponseEntity.ok("Product " + productId + " - Stock: " + stock);
```

**Pourquoi 60% d'erreurs ?** Pour dépasser rapidement le seuil de 50% du Circuit Breaker et déclencher son ouverture lors des tests.

---

### 7.3 Payment Service (port 8082)

Service volontairement lent. Il simule un service de paiement qui ferait des appels à une banque ou une API externe lente.

#### Endpoint

`GET /payment/{orderId}`

#### Comportement

```java
Thread.sleep(3000); // Attente simulée de 3 secondes
return "Payment processed for order " + orderId;
```

**Pourquoi 3 secondes ?** Pour que lors d'appels simultanés, les threads restent occupés longtemps — permettant au Bulkhead de détecter la saturation et de rejeter les appels excédentaires.

---

## 8. Prérequis et installation

### Logiciels requis

| Logiciel | Version minimale | Vérification |
|----------|-----------------|--------------|
| Java JDK | 17 | `java -version` |
| Maven | 3.6 | `mvn -version` |

### Installation sur Ubuntu/Debian

```bash
sudo apt install -y openjdk-17-jdk maven
```

### Vérification

```bash
java -version
# openjdk version "17.x.x" ...

mvn -version
# Apache Maven 3.x.x ...
```

---

## 9. Compilation et démarrage

### Étape 1 — Compiler tous les modules

Depuis la racine du projet :

```bash
mvn clean install -DskipTests
```

Cette commande compile les 3 modules dans l'ordre correct et produit les fichiers `.jar` dans chaque `target/`.

### Étape 2 — Démarrer les services

Ouvrir **3 terminaux** (ou utiliser `&` pour les lancer en arrière-plan) :

**Terminal 1 — Inventory Service**
```bash
cd inventory-service
mvn spring-boot:run
```

**Terminal 2 — Payment Service**
```bash
cd payment-service
mvn spring-boot:run
```

**Terminal 3 — Order Service**
```bash
cd order-service
mvn spring-boot:run
```

### Étape 3 — Vérifier que tout est démarré

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}

curl http://localhost:8081/inventory/P1
# "Product P1 - Stock: 42" (ou erreur simulée)

curl http://localhost:8082/payment/ORDER1
# "Payment processed for order ORDER1" (après 3 secondes)
```

---

## 10. Guide de test

### Test 1 — Circuit Breaker

**Objectif :** Déclencher l'ouverture du circuit en envoyant plusieurs requêtes consécutives.

```bash
for i in $(seq 1 12); do
  echo "Appel $i: $(curl -s http://localhost:8080/orders/check/P1)"
done
```

**Résultat attendu :**

```
Appel 1:  {"inventoryResponse":"FALLBACK: ... (reason: InternalServerError)"}   ← échec réel
Appel 2:  {"inventoryResponse":"Product P1 - Stock: 27"}                         ← succès
Appel 3:  {"inventoryResponse":"FALLBACK: ... (reason: InternalServerError)"}   ← échec réel
Appel 4:  {"inventoryResponse":"FALLBACK: ... (reason: CallNotPermittedException)"} ← CIRCUIT OUVERT
Appel 5 à 12: FALLBACK (reason: CallNotPermittedException)                       ← fast-fail
```

> Dès que `CallNotPermittedException` apparaît, le circuit est **OPEN** : l'inventory service n'est plus du tout appelé.

**Vérifier l'état du circuit :**

```bash
curl http://localhost:8080/actuator/circuitbreakers
```

```json
{
  "circuitBreakers": {
    "inventoryService": {
      "state": "OPEN",
      "failureRate": "80.0%",
      "failedCalls": 4,
      "notPermittedCalls": 8
    }
  }
}
```

---

### Test 2 — Bulkhead

**Objectif :** Envoyer 5 requêtes simultanées et vérifier que seules 2 passent.

```bash
for i in 1 2 3 4 5; do
  curl -s http://localhost:8080/orders/pay/ORDER$i &
done; wait
```

**Résultat attendu :**

```
{"paymentResponse":"Payment processed for order ORDER1"}   ← accepté
{"paymentResponse":"Payment processed for order ORDER2"}   ← accepté
{"paymentResponse":"REJECTED: Bulkhead full ...ORDER3"}    ← rejeté immédiatement
{"paymentResponse":"REJECTED: Bulkhead full ...ORDER4"}    ← rejeté immédiatement
{"paymentResponse":"REJECTED: Bulkhead full ...ORDER5"}    ← rejeté immédiatement
```

> 2 requêtes passent (max concurrent = 2), les 3 autres sont rejetées sans attendre.

---

### Test 3 — Actuator (monitoring)

```bash
# État de santé global
curl http://localhost:8080/actuator/health

# État du Circuit Breaker (CLOSED / OPEN / HALF_OPEN)
curl http://localhost:8080/actuator/circuitbreakers

# État du Bulkhead
curl http://localhost:8080/actuator/bulkheads
```

---

## 11. Référence de configuration

Fichier : `order-service/src/main/resources/application.yml`

### Circuit Breaker

| Paramètre | Valeur | Signification |
|-----------|--------|---------------|
| `sliding-window-type` | `COUNT_BASED` | La fenêtre d'analyse est basée sur le nombre d'appels (pas le temps) |
| `sliding-window-size` | `10` | Les 10 derniers appels sont analysés |
| `minimum-number-of-calls` | `5` | Il faut au moins 5 appels avant d'évaluer le taux d'échec |
| `failure-rate-threshold` | `50` | Si ≥ 50% des appels échouent → circuit s'ouvre |
| `wait-duration-in-open-state` | `10s` | Le circuit reste OPEN pendant 10 secondes avant de passer en HALF_OPEN |
| `permitted-number-of-calls-in-half-open-state` | `3` | En HALF_OPEN, 3 appels test sont autorisés |
| `automatic-transition-from-open-to-half-open-enabled` | `true` | Transition automatique OPEN → HALF_OPEN après le délai |

### Bulkhead

| Paramètre | Valeur | Signification |
|-----------|--------|---------------|
| `max-concurrent-calls` | `2` | Maximum 2 appels simultanés vers payment-service |
| `max-wait-duration` | `0` | Si le bulkhead est plein, rejet immédiat (pas d'attente) |

---

## 12. Correctifs appliqués

Lors de la mise en place du projet, deux problèmes ont été corrigés dans le `pom.xml` racine.

### Correctif 1 — Conflit de version SLF4J

**Problème :** `resilience4j-spring-boot3` 2.2.0 tirait transitivement `slf4j-api:1.7.30`, incompatible avec Logback de Spring Boot 3 qui nécessite `slf4j-api:2.x`.

**Symptôme :** `IllegalArgumentException: LoggerFactory is not a Logback LoggerContext`

**Solution :** Forcer la version de `slf4j-api` via `<dependencyManagement>` dans le POM parent :

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

### Correctif 2 — Flag `-parameters` du compilateur

**Problème :** Spring 6 nécessite que les noms des paramètres de méthode soient présents dans le bytecode pour résoudre automatiquement les `@PathVariable` sans valeur explicite. Sans le flag `-parameters`, le compilateur ne les inclut pas.

**Symptôme :** `IllegalArgumentException: Name for argument of type [String] not specified`

**Solution :** Ajouter le flag `-parameters` au plugin `maven-compiler-plugin` dans le POM parent :

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <compilerArgs>
                    <arg>-parameters</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

*Document généré le 14 mai 2026 — Projet ING252 Architecture Logicielle, INGA2 Groupe 1*
