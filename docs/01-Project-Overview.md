# 01 — Project Overview

> **Navigation:** [README](README.md) | [Architecture Decisions →](02-Architecture-Decisions.md)

---

## Table of Contents

1. [What We Are Building](#1-what-we-are-building)
2. [Why We Are Building It](#2-why-we-are-building-it)
3. [Business Goals](#3-business-goals)
4. [Functional Requirements](#4-functional-requirements)
5. [Non-Functional Requirements](#5-non-functional-requirements)
6. [Assumptions](#6-assumptions)
7. [Constraints](#7-constraints)
8. [Future Scope](#8-future-scope)

---

## 1. What We Are Building

We are building an **Enterprise Banking Backend System** — a production-ready, cloud-native backend platform that replicates the core capabilities of modern digital banks like HDFC, ICICI, Revolut, and Chase.

The system exposes a set of secure, versioned REST APIs consumed by:
- Mobile banking applications (iOS / Android)
- Web banking portals
- Third-party fintech applications via open banking APIs
- Internal admin dashboards

The platform handles the full customer lifecycle: onboarding, KYC verification, account management, fund transfers, UPI payments, fraud detection, statement generation, and notifications.

This is **not a CRUD application**. It is a distributed, event-driven, microservice system built to handle:
- Concurrent financial transactions with strict consistency guarantees
- Real-time fraud detection
- Regulatory audit trails
- High availability with zero downtime deployments

---

## 2. Why We Are Building It

### The Market Problem

Traditional banking backends are typically:
- **Monolithic** — one failed deploy takes down the entire bank
- **Tightly coupled** — changing one module risks breaking others
- **Not cloud-native** — manual scaling, no containerization
- **Synchronous** — blocking I/O creates bottlenecks under load
- **Opaque** — poor observability and audit trails

### Our Solution

We are building a **cloud-native microservice platform** that:
- Deploys and scales each service independently
- Communicates asynchronously via Kafka for resilience
- Provides complete audit trails for regulatory compliance
- Detects fraud in real time using rule engines
- Supports millions of concurrent users via horizontal scaling

---

## 3. Business Goals

| Goal | Description | Success Metric |
|------|-------------|----------------|
| **Customer Acquisition** | Fast, friction-free digital onboarding | < 3 minutes to create account |
| **Transaction Reliability** | Zero money loss from system failures | 99.999% transaction durability |
| **Fraud Prevention** | Block fraudulent transactions before completion | < 0.01% fraud rate |
| **Regulatory Compliance** | RBI/NPCI compliance for UPI, KYC, AML | 100% audit trail completeness |
| **Platform Availability** | No single point of failure | 99.99% SLA (52 min downtime/year) |
| **Developer Velocity** | Independent team ownership per service | < 1 hour to deploy a single service |
| **Cost Efficiency** | Scale down idle services, scale up under load | 40% infrastructure cost reduction vs monolith |

---

## 4. Functional Requirements

### 4.1 Customer Management

| ID | Requirement |
|----|-------------|
| CM-01 | Customer can self-register with mobile number and email |
| CM-02 | OTP-based mobile verification during registration |
| CM-03 | KYC submission with Aadhaar/PAN document upload |
| CM-04 | KYC approval/rejection by admin |
| CM-05 | Customer profile update (address, email, nominee) |
| CM-06 | Customer account freeze by admin or automated fraud trigger |
| CM-07 | Customer deactivation and data retention per policy |

### 4.2 Account Management

| ID | Requirement |
|----|-------------|
| AC-01 | Create savings/current account after KYC approval |
| AC-02 | Real-time balance inquiry |
| AC-03 | Account freeze (partial: debit only / credit only / full) |
| AC-04 | Account closure with balance settlement |
| AC-05 | Multiple accounts per customer |
| AC-06 | Account number generation (unique, masked in APIs) |

### 4.3 Transaction Management

| ID | Requirement |
|----|-------------|
| TR-01 | Cash deposit (teller/ATM simulation) |
| TR-02 | Cash withdrawal |
| TR-03 | Internal fund transfer (same bank) |
| TR-04 | NEFT/RTGS/IMPS transfer (external bank simulation) |
| TR-05 | Transaction reversal within T+1 day |
| TR-06 | Transaction history with pagination and filtering |
| TR-07 | Idempotent transaction APIs (duplicate prevention) |
| TR-08 | Daily/monthly transaction limits per account type |

### 4.4 Beneficiary Management

| ID | Requirement |
|----|-------------|
| BN-01 | Add beneficiary (name, account number, IFSC) |
| BN-02 | Beneficiary verification via penny-drop |
| BN-03 | Remove beneficiary |
| BN-04 | Cooldown period before first transfer to new beneficiary |
| BN-05 | Maximum beneficiary limit per customer |

### 4.5 UPI

| ID | Requirement |
|----|-------------|
| UP-01 | Create UPI VPA (Virtual Payment Address) |
| UP-02 | Link UPI to account |
| UP-03 | Set/change UPI PIN |
| UP-04 | UPI transfer (P2P and P2M) |
| UP-05 | Daily UPI limit enforcement |
| UP-06 | UPI transaction history |
| UP-07 | Deactivate UPI ID |

### 4.6 Notifications

| ID | Requirement |
|----|-------------|
| NO-01 | SMS on every debit/credit transaction |
| NO-02 | Email on account opening, password reset, large transactions |
| NO-03 | Push notification (mobile app) for real-time alerts |
| NO-04 | Notification preferences management |
| NO-05 | Notification retry on delivery failure |

### 4.7 Fraud Detection

| ID | Requirement |
|----|-------------|
| FR-01 | Velocity check: block > N transactions per hour |
| FR-02 | Blacklist check: block transactions to/from blacklisted accounts |
| FR-03 | Large transaction flag: alert on transactions above threshold |
| FR-04 | Geolocation anomaly detection (future) |
| FR-05 | Automatic account freeze on fraud trigger |
| FR-06 | Fraud alert dashboard for admin |

### 4.8 Statement

| ID | Requirement |
|----|-------------|
| ST-01 | Monthly statement generation |
| ST-02 | PDF download of statement |
| ST-03 | Statement email delivery |
| ST-04 | Custom date range statement |
| ST-05 | Statement stored for 7 years (regulatory) |

### 4.9 Admin

| ID | Requirement |
|----|-------------|
| AD-01 | View all customers and their status |
| AD-02 | Freeze/unfreeze accounts |
| AD-03 | Approve/reject KYC |
| AD-04 | View all transactions with filters |
| AD-05 | View fraud alerts and take action |
| AD-06 | Role-based access (Super Admin, Ops, Auditor) |

---

## 5. Non-Functional Requirements

### 5.1 Performance

| Metric | Target |
|--------|--------|
| API Response Time (p95) | < 200ms for reads |
| API Response Time (p95) | < 500ms for transaction writes |
| Throughput | 10,000 TPS at peak |
| Kafka Lag | < 5 seconds end-to-end event processing |

### 5.2 Availability

| Metric | Target |
|--------|--------|
| System Uptime | 99.99% (≈ 52 minutes downtime/year) |
| Database Uptime | 99.999% (PostgreSQL with read replicas + failover) |
| Kafka Availability | 99.99% (multi-broker cluster) |
| Zero downtime deployment | Rolling deployment per service |

### 5.3 Scalability

- Horizontal scaling for all stateless services
- Database read replicas for read-heavy services
- Kafka partition scaling for increased throughput
- Redis cluster mode for cache scaling

### 5.4 Security

- All APIs require JWT authentication
- Sensitive data encrypted at rest (AES-256)
- TLS 1.3 in transit
- PII masked in logs
- Role-based access control (RBAC)
- Rate limiting at API Gateway
- OWASP Top 10 protection

### 5.5 Reliability

- At-least-once event delivery via Outbox Pattern
- Idempotent APIs prevent duplicate transactions
- Circuit breaker for external service calls
- Saga pattern for distributed transaction consistency
- Automatic retry with exponential backoff

### 5.6 Observability

- Structured JSON logging with correlation IDs
- Distributed tracing (Jaeger/Zipkin — future)
- Metrics: Prometheus scrape every 15 seconds
- Dashboards: Grafana with SLA alerting
- Health checks: `/actuator/health`, `/actuator/readiness`

### 5.7 Maintainability

- Clean Architecture per service
- SOLID principles enforced
- OpenAPI documentation auto-generated
- Flyway migrations versioned and reproducible
- TestContainers for integration tests (no mock databases)

### 5.8 Compliance

- RBI Digital Payments Security Controls
- NPCI UPI guidelines
- GDPR-equivalent data handling (PII management)
- AML (Anti-Money Laundering) velocity checks
- 7-year transaction audit retention

---

## 6. Assumptions

1. **Single Country (India)** — Initial launch targets India; internationalization deferred.
2. **NPCI UPI** — UPI is simulated internally; real NPCI integration is a future milestone.
3. **KYC** — Document upload handled by the platform; third-party KYC verification (e.g., DigiLocker) is a future integration.
4. **Notification providers** — Email via SMTP (configurable), SMS via Twilio, Push via FCM. Provider credentials injected via environment variables.
5. **Authentication** — Mobile/email + password + OTP. Biometric is a future feature.
6. **Currency** — INR only in v1.
7. **No physical branch** — This is a digital-only banking backend.
8. **Internal services trust** — Services within the cluster trust each other via service mesh (mTLS deferred to v2).
9. **Storage** — Statement PDFs stored in MinIO (S3-compatible) locally; AWS S3 in production.
10. **Machine Learning** — Rule-based fraud detection in v1; ML model integration in v2.

---

## 7. Constraints

| Constraint | Impact |
|-----------|--------|
| **Java 21** | LTS version; use virtual threads (Project Loom) where beneficial |
| **Spring Boot** | Framework conventions must be followed; avoid raw servlet code |
| **PostgreSQL only** | No polyglot persistence in v1; all services use PostgreSQL |
| **Single Kafka cluster** | All services share one Kafka cluster; topic isolation via naming |
| **No 2PC** | Distributed transactions handled via Saga; no XA transactions |
| **Budget** | Docker Compose for local dev; Kubernetes deferred to v2 |
| **Team size** | Small team — each microservice should have minimal boilerplate |
| **Regulatory** | Must retain financial records for 7 years; deletion requires audit |

---

## 8. Future Scope

| Feature | Description | Phase |
|---------|-------------|-------|
| Internet Banking Portal | Full web portal for retail customers | v2 |
| Mobile Banking App | iOS/Android apps | v2 |
| Loan Management | Personal, home, auto loan processing | v2 |
| Credit Cards | Card issuance, billing, rewards | v3 |
| International Transfers | SWIFT integration | v3 |
| Multi-Currency Accounts | Hold and transact in multiple currencies | v3 |
| AI Fraud Detection | ML models replacing rule engine | v2 |
| Open Banking APIs | Third-party fintech integration | v2 |
| Investment Accounts | Mutual funds, FDs, stocks | v3 |
| QR Code Payments | Merchant QR for in-store payments | v2 |
| Recurring Payments | Standing instructions, auto-debit | v2 |
| Bill Payments | Utility bills via BBPS | v2 |
| Blockchain Audit | Immutable audit trail on private chain | v4 |
| Biometric Auth | Fingerprint/Face ID | v2 |

---

> **Next:** [Architecture Decisions →](02-Architecture-Decisions.md)
