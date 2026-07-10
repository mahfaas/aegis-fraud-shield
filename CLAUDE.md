# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Aegis Fraud-Shield is a real-time fraud detection engine for financial transactions built on Spring Boot 3.4.4 / Java 24. Transactions flow in via Kafka, are evaluated by a Chain-of-Responsibility rule engine, and a verdict (`APPROVED` / `MANUAL_REVIEW` / `DECLINED`) is published back out and audited to PostgreSQL. Redis backs velocity/geo-velocity checks; the whole stack is observable via Micrometer/Prometheus/Grafana.

## Commands

- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=RuleEngineTest`
- Run a single test method: `./mvnw test -Dtest=RuleEngineTest#methodName`
- Build (skip tests): `./mvnw package -DskipTests`
- Run the app locally: `./mvnw spring-boot:run` (requires Postgres/Redis/Kafka already up)
- Full stack via Docker: `docker-compose up --build -d` (app waits for Postgres/Kafka health checks)
- Generate synthetic load once running: `curl -X POST "http://localhost:8080/api/v1/producer/generate?count=1000"`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Grafana: `http://localhost:3000` (admin/admin, dashboard pre-provisioned)

Integration tests use Testcontainers (Postgres, Kafka, Redis) and require Docker to be running — see `src/test/java/.../integration/`.

## Architecture

### Pipeline (Kafka-driven)

`TransactionProducer` → Kafka `transactions-raw` → `TransactionConsumer.consume()` → `TransactionValidator` (invalid → `transactions-dlq`) → `RuleEngine.evaluate()` → `VerdictProducer` → Kafka `transactions-verdicted`, plus `AuditService.record()` persists every verdict to Postgres.

`TransactionConsumer` (`kafka/TransactionConsumer.java`) is the orchestration point: it sets MDC (`transactionId`, `accountId`) for structured logging, checks `IdempotencyService` to drop Kafka redeliveries, validates, runs the rule engine, publishes the verdict, and records the audit entry — all wrapped so MDC is always cleared in `finally`.

### Rule engine (Chain of Responsibility)

- `engine/Rule.java` — interface every rule implements (`evaluate`, `getName`, `getOrder`).
- `engine/RuleEngine.java` — Spring auto-injects all `Rule` beans, sorted by `getOrder()`. Iterates rules, accumulates `totalRiskScore`, and short-circuits on the first `DECLINED`. A `MANUAL_REVIEW` from any rule escalates the final verdict unless a later rule declines.
- Individual rules live in `engine/rules/`: `BlacklistRule`, `AmountAnomalyRule`, `VelocityRule` (Redis-backed), `GeoVelocityRule` (Redis-backed, toggleable via `fraud.rules.geo-velocity.enabled`), `MerchantCategoryRule` (opt-in via `fraud.rules.merchant-category.enabled`), `TimeWindowRule`.
- `RiskScoreThresholdRule` is a special *composite* rule that always runs last (`getOrder() == 50`). It reads the running total via a `ThreadLocal` (`engine/rules/RuleContext`), which `RuleEngine` populates before every rule invocation. This lets several "soft" signals that individually wouldn't trigger a verdict combine into a `MANUAL_REVIEW` (soft threshold) or `DECLINED` (hard threshold). `RuleContext.clear()` is always called in the engine's `finally` block to avoid leaking state across Kafka consumer thread reuse — preserve this pattern if you add rules that depend on accumulated state.
- New rules just need to be a `@Component` implementing `Rule`; no registration list to update. Order matters only relative to `RiskScoreThresholdRule` (must stay last) and any rule that depends on another's side effects.

### Fraud case management (`cases/`)

`FraudCaseService` implements a manual, framework-free state machine for analyst review: `OPEN → INVESTIGATING → {CLOSED_FRAUD | CLOSED_LEGITIMATE}`. Only forward transitions are allowed (enforced in `validateTransition`); backward/repeat transitions on a closed case return `409 Conflict`. Cases are created automatically and idempotently (`existsByTransactionId` guard) from `AuditService.record()` whenever a `MANUAL_REVIEW` verdict is produced — there's no separate "create case" API call in the happy path.

### Config (`config/`)

Rule thresholds are dual-sourced: static defaults come from `application.yaml` under `fraud.rules.*`, but several rules (e.g. merchant category lists, risk-score thresholds) are mutable at runtime via `RuleConfigController` / `RuleConfigDbController`, backed by `RuleConfigEntity`/`RuleConfigTagEntity` in Postgres. When touching rule thresholds, check whether the value is meant to be live-adjustable via the config API rather than hardcoded.

### Cross-cutting

- `metrics/FraudMetrics.java` — Micrometer counters/timers for received/verdict/DLQ/rule-triggered events; wire new signals through here rather than ad hoc `MeterRegistry` calls.
- `exception/` + `api/GlobalExceptionHandler.java` — all API errors funnel through a `@RestControllerAdvice` for a consistent JSON error shape.
- `security/RateLimitFilter.java` — API rate limiting, configured under `fraud.api.rate-limit.*`.
- DB schema changes go through Flyway migrations in `src/main/resources/db/migration/` (`V1__...` … `V6__...`); add a new versioned migration rather than editing an existing one.
