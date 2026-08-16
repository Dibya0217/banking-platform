# Enterprise Banking Backend System

> Production-grade banking platform built with Java 21, Spring Boot, PostgreSQL, Kafka, and Redis.  
> Designed as a Microservice Architecture capable of supporting millions of users.

---

## Table of Contents

| # | Document | Description |
|---|----------|-------------|
| — | **You are here** | Navigation index |
| 01 | [Project Overview](01-Project-Overview.md) | Vision, goals, requirements, constraints |
| 02 | [Architecture Decisions](02-Architecture-Decisions.md) | ADR-style rationale for every technology choice |
| 03 | [High Level Design](03-HLD.md) | System diagrams, flows, scaling, HA, DR |
| 04 | [Low Level Design](04-LLD.md) | Per-service design, class/sequence diagrams |
| 05 | [Database Design](05-Database-Design.md) | All tables, ER diagrams, indexes, constraints |
| 06 | [API Design](06-API-Design.md) | Every REST endpoint, request/response, auth |
| 07 | [Kafka Design](07-Kafka-Design.md) | Topics, consumers, retry, DLQ, schema |
| 08 | [Redis Design](08-Redis-Design.md) | Caching, locking, rate limiting, OTP |
| 09 | [Security](09-Security.md) | Auth, JWT, RBAC, encryption, audit |
| 10 | [Deployment](10-Deployment.md) | Docker, CI/CD, monitoring, Kubernetes |
| 11 | [UML Diagrams](11-UML-Diagrams.md) | Class, sequence, activity, state, component |
| 12 | [Folder Structure](12-Folder-Structure.md) | Per-service Maven module layout |
| 13 | [Design Patterns](13-Design-Patterns.md) | Pattern usage across all services |
| 14 | [Future Enhancements](14-Future-Enhancements.md) | Loans, cards, AI, blockchain, international |

---

## System at a Glance

```
Client (Mobile/Web/Third-Party)
        │
        ▼
  [ API Gateway ]  ← JWT validation, rate limiting, routing
        │
  ┌─────┴──────────────────────────────────┐
  │                                         │
Auth   Customer   Account   Transaction   UPI
Service  Service   Service    Service    Service
  │         │         │           │         │
  └─────────┴─────────┴─────┬─────┴─────────┘
                             │
                         [ Kafka ]
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
    Notification         Fraud Detection     Audit
      Service               Service          Service
          │
    Statement / Admin Services
```

---

## Technology Stack Summary

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| API Style | REST (OpenAPI 3.0) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Message Broker | Apache Kafka 3.x |
| Security | Spring Security + JWT |
| ORM | Spring Data JPA / Hibernate |
| Migration | Flyway |
| Build | Maven |
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Monitoring | Prometheus + Grafana + Spring Actuator |
| Logging | ELK Stack (Elasticsearch + Logstash + Kibana) |
| Testing | JUnit 5 + Mockito + TestContainers |

---

## Core Services

| Service | Responsibility |
|---------|---------------|
| API Gateway | Edge routing, auth validation, rate limiting |
| Auth Service | JWT issuance, refresh, revocation |
| Customer Service | Registration, KYC, profile management |
| Account Service | Account lifecycle, balance, freeze |
| Transaction Service | Deposits, withdrawals, transfers, reversals |
| Beneficiary Service | Add/remove/verify payees |
| UPI Service | VPA management, UPI transfers |
| Notification Service | Email, SMS, Push notifications |
| Fraud Detection Service | Velocity checks, blacklist, anomaly detection |
| Statement Service | PDF generation, monthly statements |
| Admin Service | Internal operations dashboard |
| Audit Service | Immutable event audit trail |
| Config Service | Centralized configuration |

---

## Quick Start (Development)

```bash
# Clone repository
git clone https://github.com/Dibya0217/banking-platform.git
cd banking-platform

# Start all infrastructure
docker-compose up -d

# Run a specific service
cd services/auth-service
mvn spring-boot:run
```

> See [Deployment Guide](10-Deployment.md) for full environment setup.

---

## Key Design Principles

- **Domain-Driven Design** — each service owns its bounded context
- **Event-Driven** — services communicate asynchronously via Kafka
- **Saga Pattern** — distributed transactions without 2PC
- **Outbox Pattern** — guaranteed at-least-once event delivery
- **CQRS** — separate read/write models for high-throughput services
- **Optimistic Locking** — high-concurrency balance updates
- **Idempotency** — every mutation API is idempotent
- **Zero Trust** — JWT validated at gateway; service-to-service mTLS (future)

---

*Last Updated: 2026-08-15 | Version: 1.0.0 | Author: Architecture Team*
