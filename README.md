<div align="center">
  
  <h1>🛡 Aegis Fraud-Shield</h1>
  <p><strong>Enterprise-Grade Real-Time Fraud Detection Engine</strong></p>
  
  <p>
    <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21" />
    <img src="https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen.svg" alt="Spring Boot" />
    <img src="https://img.shields.io/badge/Kafka-Event%20Streaming-black.svg" alt="Kafka" />
    <img src="https://img.shields.io/badge/Redis-Caching-red.svg" alt="Redis" />
    <img src="https://img.shields.io/badge/PostgreSQL-Database-blue.svg" alt="PostgreSQL" />
  </p>
</div>

---

## 📖 Overview

Aegis Fraud-Shield is a high-performance system designed to evaluate financial transactions in real-time. It detects anomalies, velocity spikes, and blacklisted entities using a flexible *Chain of Responsibility* rule engine. 

### ✨ Key Features

- ⚡️ Low Latency: In-memory and Redis caching for blazing fast validations.
- 🔗 Event-Driven: Fully decoupled, scalable architecture using Apache Kafka.
- 🧱 Extensible Rules Execution: Easily plug in new fraud detection strategies without modifying the core.
- 📊 Rich Observability: Built-in Micrometer, Prometheus, and Grafana stack for monitoring TPS, latency, and rule triggers.
- 🌐 Dynamic Configuration: Adjust fraud thresholds on-the-fly via REST API with zero downtime.
- 🛠 Automated Generation: Integrated synthetic transaction producer for immediate load testing.

---

## 🏗 Architecture

```mermaid
graph TB
    subgraph "Event Source"
        P["Transaction Producer"] -->|JSON| KIn["Kafka: transactions-raw"]
    end

    subgraph "Core Engine (Spring Boot)"
        C["Kafka Consumer"] --> V["Validator"]
        V -->|Invalid| KDLQ["Kafka: transactions-dlq"]
        V -->|Valid| RE{"Rule Engine"}
        
        RE --> R1["Blacklist Rule"]
        R1 -.-> R2["Amount Anomaly Rule"]
        R2 -.-> R3["Velocity Rule"]
        R3 -.-> R4["Geo Velocity Rule"]
        
        R4 --> RP["Verdict Producer"]
    end

    subgraph "Data Storage"
        Redis[("Redis<br/>(Velocity & TTL)")]
        PG[("PostgreSQL<br/>(Config & Blacklist)")]
    end

    RE <--> Redis
    RE <--> PG

    RP -->|APPROVED / DECLINED| KOut["Kafka: transactions-verdicted"]
```

---

## 🚦 Fraud Detection Rules

| Rule | Description | Backing Store | Time Complexity |
|---|---|---|---|
| Blacklist | O(1) checks against known malicious IPs and Card BINs | PostgreSQL + Memory | O(1) |
| Amount Anomaly| Flags transactions exceeding highly configurable risk limits | Memory / DB Sync | O(1) |
| Velocity | Detects high-frequency spending patterns per account using TTL counters | Redis | O(1) |
| Geo-Velocity | Prevents "impossible travel" by evaluating IP geos within timeframes | Redis | O(1) |

---

## 🚀 Getting Started

### 1. Prerequisites
- Java 21 or higher
- Docker & Docker Compose
- Maven (or use the included `./mvnw` wrapper)

### 2. Launch Infrastructure
Start all necessary dependency services (Kafka, Zookeeper, Redis, PostgreSQL, Prometheus, Grafana) with a single command:
```bash
docker-compose up -d
```

### 3. Run the Core Application
The application automatically applies database migrations using Flyway on startup.
```bash
# Wait a few seconds for Kafka & DB to initialize
./mvnw spring-boot:run
```

---

## 🔗 API Integration & Dashboard

Explore and interact with the REST API using the embedded Swagger UI:  
👉 `http://localhost:8080/swagger-ui.html`

### 🎮 Load Generation
Generate synthetic transaction data directly via cURL to observe the engine's behavior under load:
```bash
curl -X POST "http://localhost:8080/api/v1/producer/generate?count=1000"
```

### 📈 Monitoring Metrics
Observe system metrics in real-time through Grafana:
- 🌐 URL: `http://localhost:3000`
- 🔒 Credentials: `admin` / `admin`
*(The Aegis dashboard is pre-provisioned under the "Dashboards" tab)*

---

## 🧪 Testing
The project includes an extensive suite of 40+ unit tests using JUnit 5 and Mockito. To execute:
```bash
./mvnw test
```