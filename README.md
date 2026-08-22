# Enterprise Banking Backend

A production-grade, microservices-based banking platform built with **Spring Boot 3**, **Java 21**, **Kafka**, **PostgreSQL**, **Redis**, and **Spring Cloud**.

## Architecture

```
                        ┌─────────────────────┐
                        │     API Gateway      │  :8080
                        │  (JWT · RateLimit)   │
                        └────────┬────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                       │
   ┌──────▼──────┐  ┌────────────▼───────┐  ┌──────────▼──────┐
   │ Auth Service │  │  Customer Service  │  │ Account Service  │
   │    :8081     │  │       :8082        │  │      :8083       │
   └─────────────┘  └────────────────────┘  └─────────────────┘
          │                      │                       │
   ┌──────▼──────────────────────▼───────────────────────▼──────┐
   │                         Kafka                               │
   └──────┬──────────────────────┬───────────────────────┬──────┘
          │                      │                       │
   ┌──────▼──────┐  ┌────────────▼───────┐  ┌──────────▼──────┐
   │ Transaction  │  │  Fraud Detection   │  │  Notification    │
   │   :8084      │  │       :8090        │  │     :8089        │
   └─────────────┘  └────────────────────┘  └─────────────────┘
          │
   ┌──────┴──────────────────────────────────────────────┐
   │  Beneficiary :8085 · UPI :8086 · Statement :8091    │
   │  Admin :8093 · Audit :8092                          │
   └─────────────────────────────────────────────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| api-gateway | 8080 | JWT auth, rate limiting, routing |
| auth-service | 8081 | Login, OTP, JWT issuance, token blacklist |
| customer-service | 8082 | Registration, KYC, profile |
| account-service | 8083 | Account creation, balance (Redis cache), optimistic locking |
| transaction-service | 8084 | Deposit, withdraw, transfer (Choreography Saga) |
| beneficiary-service | 8085 | Add/verify beneficiaries, cooldown enforcement |
| upi-service | 8086 | VPA management, PIN, daily limits |
| notification-service | 8089 | Email/SMS/Push via Kafka events |
| fraud-detection-service | 8090 | Velocity check, blacklist, large-transaction rules |
| statement-service | 8091 | PDF statements, MinIO storage |
| audit-service | 8092 | Append-only audit log from all Kafka events |
| admin-service | 8093 | Admin operations (KYC approval, freeze accounts) |
| eureka-service | 8761 | Service registry |
| config-service | 8888 | Centralized configuration |

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL 16 | 5434 | Primary database (9 schemas) |
| Redis 7 | 6380 | Token blacklist, OTP, balance cache, rate limits |
| Kafka | 9092 | Event streaming between services |
| MinIO | 9094 | PDF statement storage |
| MailHog | 8025 | Local email testing |
| Prometheus | 9093 | Metrics scraping |
| Grafana | 3000 | Dashboards |
| Elasticsearch | 9200 | Log aggregation |
| Kibana | 5601 | Log visualization |

## Quick Start

### Prerequisites
- Docker Desktop
- Java 21 (Temurin)
- Maven 3.9+

### 1. Clone and configure

```bash
git clone https://github.com/Dibya0217/banking-system.git
cd banking-system
cp .env.example .env
# Edit .env with your secrets
```

### 2. Start infrastructure

```bash
cd infrastructure/docker
docker-compose up -d
docker-compose ps   # Wait until all healthy
```

### 3. Run all services

```bash
# From project root — build shared libs first
./mvnw install -pl shared/banking-commons,shared/banking-events -am

# Start each service (separate terminals or use your IDE)
./mvnw spring-boot:run -pl services/eureka-service
./mvnw spring-boot:run -pl services/config-service
./mvnw spring-boot:run -pl services/auth-service
./mvnw spring-boot:run -pl services/customer-service
./mvnw spring-boot:run -pl services/account-service
./mvnw spring-boot:run -pl services/transaction-service
./mvnw spring-boot:run -pl services/beneficiary-service
./mvnw spring-boot:run -pl services/upi-service
./mvnw spring-boot:run -pl services/fraud-detection-service
./mvnw spring-boot:run -pl services/notification-service
./mvnw spring-boot:run -pl services/statement-service
./mvnw spring-boot:run -pl services/audit-service
./mvnw spring-boot:run -pl services/admin-service
./mvnw spring-boot:run -pl services/api-gateway
```

### 4. Verify

```bash
curl http://localhost:8080/actuator/health | jq .status
# "UP"
open http://localhost:8761       # Eureka — all services registered
open http://localhost:3000       # Grafana (admin / admin123)
open http://localhost:8025       # MailHog
```

## End-to-End Flow

```bash
# 1. Register customer
curl -X POST http://localhost:8080/api/v1/customers/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Priya Sharma","email":"priya@example.com","mobile":"9876543210","password":"Test@1234","dateOfBirth":"1990-05-15"}'

# 2. Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"priya@example.com","password":"Test@1234"}'
# → copy accessToken

TOKEN="<accessToken>"

# 3. Deposit
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"accountId":"<accountId>","amount":50000,"description":"Initial deposit"}'

# 4. Transfer
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"<from>","toAccountId":"<to>","amount":5000}'
```

## Running Tests

```bash
# All services
./mvnw test

# Single service
./mvnw test -pl services/transaction-service
```

## Load Testing

```bash
# Install k6: https://k6.io/docs/getting-started/installation/
k6 run -e TOKEN=$TOKEN -e ACCOUNT_ID=$ACCOUNT_ID load-tests/balance-inquiry.js
k6 run -e TOKEN=$TOKEN -e FROM_ACCOUNT=$FROM -e TO_ACCOUNT=$TO load-tests/transfer.js
```

## Key Design Patterns

| Pattern | Where |
|---------|-------|
| Transactional Outbox | customer, account, transaction services |
| Choreography Saga | transaction-service (transfer flow) |
| Optimistic Locking | account-service (concurrent debits) |
| Cache-Aside | account-service (balance via Redis) |
| Chain of Responsibility | fraud-detection-service (rule chain) |
| Idempotency | transaction-service (Idempotency-Key header) |
| Circuit Breaker | api-gateway (Resilience4j) |
| Template Method | notification-service (BaseNotificationSender) |

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci-cd.yml`):
- Matrix build: all 14 services tested in parallel
- OWASP dependency vulnerability scan
- Docker images pushed to GHCR on merge to `main`
