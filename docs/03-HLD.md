# 03 — High Level Design (HLD)

> **Navigation:** [← Architecture Decisions](02-Architecture-Decisions.md) | [LLD →](04-LLD.md)

---

## Table of Contents

1. [System Architecture Diagram](#1-system-architecture-diagram)
2. [Service Responsibilities](#2-service-responsibilities)
3. [Request Flow](#3-request-flow)
4. [Authentication Flow](#4-authentication-flow)
5. [Transaction (Transfer) Flow](#5-transaction-transfer-flow)
6. [Fraud Detection Flow](#6-fraud-detection-flow)
7. [Notification Flow](#7-notification-flow)
8. [Event Flow (Kafka)](#8-event-flow-kafka)
9. [API Gateway Design](#9-api-gateway-design)
10. [Caching Strategy](#10-caching-strategy)
11. [Database Architecture](#11-database-architecture)
12. [Network Architecture](#12-network-architecture)
13. [Scaling Strategy](#13-scaling-strategy)
14. [High Availability](#14-high-availability)
15. [Fault Tolerance](#15-fault-tolerance)
16. [Disaster Recovery](#16-disaster-recovery)
17. [Deployment Diagram](#17-deployment-diagram)

---

## 1. System Architecture Diagram

```mermaid
graph TB
    subgraph Clients
        MB[Mobile App]
        WB[Web Browser]
        TP[Third-Party Apps]
        ADM[Admin Portal]
    end

    subgraph Edge Layer
        LB[Load Balancer<br/>Nginx/ALB]
        GW[API Gateway<br/>Spring Cloud Gateway]
    end

    subgraph Core Services
        AUTH[Auth Service<br/>:8081]
        CUST[Customer Service<br/>:8082]
        ACC[Account Service<br/>:8083]
        TXN[Transaction Service<br/>:8084]
        BEN[Beneficiary Service<br/>:8085]
        UPI[UPI Service<br/>:8086]
        STMT[Statement Service<br/>:8087]
        ADMIN[Admin Service<br/>:8088]
    end

    subgraph Async Services
        NOTIF[Notification Service<br/>:8089]
        FRAUD[Fraud Detection<br/>:8090]
        AUDIT[Audit Service<br/>:8091]
    end

    subgraph Infrastructure
        KAFKA[Apache Kafka<br/>3 Brokers]
        PG_PRIMARY[(PostgreSQL Primary)]
        PG_REPLICA[(PostgreSQL Replica)]
        REDIS[(Redis Cluster)]
        MINIO[(MinIO / S3<br/>Statements)]
    end

    subgraph Config
        EUREKA[Eureka<br/>Service Discovery]
        CONFIG[Config Service<br/>Spring Cloud Config]
    end

    subgraph Observability
        PROM[Prometheus]
        GRAF[Grafana]
        ELK[ELK Stack]
    end

    MB & WB & TP & ADM --> LB
    LB --> GW
    GW --> AUTH
    GW --> CUST
    GW --> ACC
    GW --> TXN
    GW --> BEN
    GW --> UPI
    GW --> STMT
    GW --> ADMIN

    TXN --> KAFKA
    CUST --> KAFKA
    ACC --> KAFKA
    UPI --> KAFKA

    KAFKA --> NOTIF
    KAFKA --> FRAUD
    KAFKA --> AUDIT
    KAFKA --> STMT

    AUTH --> REDIS
    TXN --> REDIS
    ACC --> REDIS
    GW --> REDIS

    CUST --> PG_PRIMARY
    ACC --> PG_PRIMARY
    TXN --> PG_PRIMARY
    BEN --> PG_PRIMARY
    UPI --> PG_PRIMARY

    TXN -.-> PG_REPLICA
    STMT -.-> PG_REPLICA
    ADMIN -.-> PG_REPLICA

    STMT --> MINIO

    Core Services --> EUREKA
    Async Services --> EUREKA
    Core Services --> CONFIG
```

---

## 2. Service Responsibilities

| Service | Port | Type | Owns DB | Publishes Events | Consumes Events |
|---------|------|------|---------|-----------------|-----------------|
| API Gateway | 8080 | Edge | No | No | No |
| Auth Service | 8081 | Core | Yes (auth DB) | `auth.token.revoked` | No |
| Customer Service | 8082 | Core | Yes (customer DB) | `customer.registered`, `customer.kyc.approved`, `customer.frozen` | No |
| Account Service | 8083 | Core | Yes (account DB) | `account.created`, `account.debited`, `account.credited`, `account.frozen` | `customer.kyc.approved` |
| Transaction Service | 8084 | Core | Yes (txn DB) | `transaction.initiated`, `transaction.completed`, `transaction.failed`, `transaction.reversed` | `account.debited`, `account.credited` |
| Beneficiary Service | 8085 | Core | Yes (beneficiary DB) | `beneficiary.added`, `beneficiary.removed` | No |
| UPI Service | 8086 | Core | Yes (upi DB) | `upi.transfer.completed`, `upi.transfer.failed` | No |
| Statement Service | 8087 | Core | No (read replica) | No | `transaction.completed` |
| Admin Service | 8088 | Core | No (read replica) | `admin.account.frozen` | No |
| Notification Service | 8089 | Async | Yes (notification DB) | No | All domain events |
| Fraud Detection | 8090 | Async | Yes (fraud DB) | `fraud.alert.raised`, `fraud.account.frozen` | `transaction.initiated` |
| Audit Service | 8091 | Async | Yes (audit DB) | No | All domain events |
| Config Service | 8888 | Infra | No | No | No |
| Eureka | 8761 | Infra | No | No | No |

---

## 3. Request Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant LB as Load Balancer
    participant GW as API Gateway
    participant R as Redis
    participant S as Target Service
    participant DB as PostgreSQL

    C->>LB: HTTPS Request + JWT
    LB->>GW: Route to Gateway
    GW->>GW: Extract JWT from Authorization header
    GW->>R: Check token blacklist (JTI lookup)
    R-->>GW: Not blacklisted
    GW->>GW: Validate JWT signature + expiry
    GW->>GW: Apply rate limit check (Redis counter)
    GW->>GW: Add X-User-Id, X-User-Roles headers
    GW->>S: Forward request with identity headers
    S->>DB: Execute business logic
    DB-->>S: Result
    S-->>GW: Response
    GW-->>LB: Response
    LB-->>C: HTTPS Response
```

---

## 4. Authentication Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant R as Redis
    participant DB as PostgreSQL

    Note over C, DB: Step 1 — Login
    C->>GW: POST /api/v1/auth/login {email, password}
    GW->>AUTH: Forward (no JWT required for login)
    AUTH->>DB: SELECT customer WHERE email = ?
    DB-->>AUTH: Customer record
    AUTH->>AUTH: BCrypt verify password
    AUTH->>AUTH: Generate Access Token (15 min JWT)
    AUTH->>AUTH: Generate Refresh Token (7 days JWT)
    AUTH->>R: STORE refresh_token:{userId} = refreshJti (TTL 7d)
    AUTH-->>C: {accessToken, refreshToken}

    Note over C, DB: Step 2 — API Request
    C->>GW: GET /api/v1/accounts + Bearer accessToken
    GW->>R: GET blacklist:{jti} → nil (not revoked)
    GW->>GW: Validate JWT (signature + expiry)
    GW->>TARGET: Forward with X-User-Id header
    TARGET-->>C: 200 Response

    Note over C, DB: Step 3 — Refresh
    C->>GW: POST /api/v1/auth/refresh {refreshToken}
    GW->>AUTH: Forward
    AUTH->>R: GET refresh_token:{userId} → verify stored JTI
    AUTH->>AUTH: Issue new Access Token (15 min)
    AUTH-->>C: {accessToken}

    Note over C, DB: Step 4 — Logout
    C->>GW: POST /api/v1/auth/logout + Bearer accessToken
    GW->>AUTH: Forward
    AUTH->>R: SET blacklist:{jti} = 1 (TTL = remaining token validity)
    AUTH->>R: DEL refresh_token:{userId}
    AUTH-->>C: 204 No Content
```

---

## 5. Transaction (Transfer) Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant TXN as Transaction Service
    participant R as Redis
    participant ACC as Account Service
    participant KAFKA as Kafka
    participant FRAUD as Fraud Detection
    participant NOTIF as Notification Service
    participant AUDIT as Audit Service
    participant DB_TXN as TXN DB
    participant DB_ACC as Account DB

    C->>GW: POST /api/v1/transactions/transfer<br/>{fromAccount, toAccount, amount, idempotencyKey}
    GW->>TXN: Forward request
    TXN->>R: GET idempotency:{key} → nil (new request)
    TXN->>DB_TXN: BEGIN TRANSACTION
    TXN->>DB_TXN: INSERT transaction (status=PENDING)
    TXN->>DB_TXN: INSERT outbox_event (transfer.initiated)
    TXN->>DB_TXN: COMMIT
    TXN->>R: SET idempotency:{key} = txnId (TTL 24h)
    TXN-->>C: 202 Accepted {transactionId, status: PENDING}

    Note over KAFKA,AUDIT: Async Processing
    TXN->>KAFKA: Publish transfer.initiated (via Outbox Poller)
    FRAUD->>KAFKA: Consume transfer.initiated
    FRAUD->>FRAUD: Velocity check + blacklist check
    FRAUD->>KAFKA: Publish fraud.check.passed OR fraud.alert.raised

    KAFKA->>ACC: Consume transfer.initiated (after fraud.check.passed)
    ACC->>DB_ACC: BEGIN TRANSACTION
    ACC->>DB_ACC: SELECT account FOR UPDATE (optimistic lock)
    ACC->>DB_ACC: Debit fromAccount (version check)
    ACC->>DB_ACC: INSERT outbox_event (account.debited)
    ACC->>DB_ACC: COMMIT
    ACC->>KAFKA: Publish account.debited

    KAFKA->>ACC: Consume account.debited → Credit toAccount
    ACC->>DB_ACC: Credit toAccount
    ACC->>KAFKA: Publish account.credited

    KAFKA->>TXN: Consume account.credited
    TXN->>DB_TXN: UPDATE transaction status=COMPLETED
    TXN->>KAFKA: Publish transaction.completed

    KAFKA->>NOTIF: Consume transaction.completed → Send SMS/Email
    KAFKA->>AUDIT: Consume transaction.completed → Persist audit record
```

---

## 6. Fraud Detection Flow

```mermaid
flowchart TD
    A[transaction.initiated event] --> B{Blacklist Check}
    B -->|Account blacklisted| C[Publish fraud.alert.raised]
    B -->|Clean| D{Velocity Check}
    D -->|> 10 txns/hour| C
    D -->|Within limits| E{Large Transaction Check}
    E -->|> ₹1,00,000| F[Publish fraud.alert.raised<br/>+ Continue transaction]
    E -->|Normal amount| G[Publish fraud.check.passed]
    C --> H[Update fraud_alerts table]
    H --> I{Auto-freeze threshold?}
    I -->|> 3 alerts in 24h| J[Publish fraud.account.frozen]
    I -->|Below threshold| K[Send admin alert]
    J --> L[Account Service consumes → Freeze account]
    G --> M[Account Service proceeds with transfer]
```

---

## 7. Notification Flow

```mermaid
sequenceDiagram
    participant KAFKA as Kafka
    participant NOTIF as Notification Service
    participant DB_N as Notification DB
    participant EMAIL as Email Provider<br/>(SMTP/SES)
    participant SMS as SMS Provider<br/>(Twilio)
    participant PUSH as Push Provider<br/>(FCM)

    KAFKA->>NOTIF: Consume transaction.completed
    NOTIF->>DB_N: Load customer notification preferences
    NOTIF->>DB_N: INSERT notification (status=PENDING)

    par Email
        NOTIF->>EMAIL: Send email
        EMAIL-->>NOTIF: 200 OK
        NOTIF->>DB_N: UPDATE status=SENT
    and SMS
        NOTIF->>SMS: Send SMS
        SMS-->>NOTIF: 200 OK
        NOTIF->>DB_N: UPDATE status=SENT
    and Push
        NOTIF->>PUSH: Send push notification
        PUSH-->>NOTIF: 200 OK
        NOTIF->>DB_N: UPDATE status=SENT
    end

    Note over NOTIF, PUSH: On failure → Retry with exponential backoff<br/>Max 3 retries → Dead Letter Queue
```

---

## 8. Event Flow (Kafka)

```mermaid
graph LR
    subgraph Producers
        CUST[Customer Service]
        ACC[Account Service]
        TXN[Transaction Service]
        UPI[UPI Service]
        FRAUD_P[Fraud Detection]
        ADMIN_P[Admin Service]
    end

    subgraph Topics
        T1[customer.events]
        T2[account.events]
        T3[transaction.events]
        T4[upi.events]
        T5[fraud.events]
        T6[notification.events]
        T7[audit.events]
    end

    subgraph Consumers
        NOTIF[Notification Service]
        FRAUD_C[Fraud Detection]
        AUDIT[Audit Service]
        ACC_C[Account Service]
        TXN_C[Transaction Service]
        STMT[Statement Service]
    end

    CUST --> T1
    ACC --> T2
    TXN --> T3
    UPI --> T4
    FRAUD_P --> T5
    ADMIN_P --> T5

    T1 --> NOTIF
    T1 --> AUDIT
    T1 --> ACC_C

    T2 --> NOTIF
    T2 --> AUDIT
    T2 --> TXN_C

    T3 --> NOTIF
    T3 --> FRAUD_C
    T3 --> AUDIT
    T3 --> STMT

    T4 --> NOTIF
    T4 --> AUDIT

    T5 --> NOTIF
    T5 --> AUDIT
    T5 --> ACC_C
```

---

## 9. API Gateway Design

```mermaid
graph TD
    Client --> GW[Spring Cloud Gateway]

    GW --> |Rate Limit Filter| RL[Redis Rate Limiter<br/>100 req/min per user]
    RL --> |JWT Auth Filter| JWT[JWT Validator<br/>+ Blacklist Check]
    JWT --> |Correlation ID Filter| CID[Add X-Correlation-Id header]
    CID --> |Logging Filter| LOG[Request/Response Logger]
    LOG --> |Route| ROUTE{Route Matcher}

    ROUTE --> |/api/v1/auth/**| AUTH[Auth Service]
    ROUTE --> |/api/v1/customers/**| CUST[Customer Service]
    ROUTE --> |/api/v1/accounts/**| ACC[Account Service]
    ROUTE --> |/api/v1/transactions/**| TXN[Transaction Service]
    ROUTE --> |/api/v1/beneficiaries/**| BEN[Beneficiary Service]
    ROUTE --> |/api/v1/upi/**| UPI[UPI Service]
    ROUTE --> |/api/v1/statements/**| STMT[Statement Service]
    ROUTE --> |/api/v1/admin/**| ADMIN[Admin Service]

    ROUTE --> |Circuit Breaker| CB[Resilience4j<br/>Fallback → 503]
```

### Gateway Route Configuration

| Path Pattern | Target Service | Auth Required | Roles |
|-------------|---------------|---------------|-------|
| `/api/v1/auth/login` | Auth Service | No | Public |
| `/api/v1/auth/refresh` | Auth Service | No | Public |
| `/api/v1/customers/register` | Customer Service | No | Public |
| `/api/v1/customers/**` | Customer Service | Yes | CUSTOMER, ADMIN |
| `/api/v1/accounts/**` | Account Service | Yes | CUSTOMER, ADMIN |
| `/api/v1/transactions/**` | Transaction Service | Yes | CUSTOMER, ADMIN |
| `/api/v1/beneficiaries/**` | Beneficiary Service | Yes | CUSTOMER |
| `/api/v1/upi/**` | UPI Service | Yes | CUSTOMER |
| `/api/v1/statements/**` | Statement Service | Yes | CUSTOMER, ADMIN |
| `/api/v1/admin/**` | Admin Service | Yes | ADMIN, SUPER_ADMIN |

---

## 10. Caching Strategy

| Cache | Key Pattern | TTL | Service | Data |
|-------|------------|-----|---------|------|
| Account Balance | `balance:{accountId}` | 30 sec | Account Service | Current balance (read cache) |
| JWT Blacklist | `blacklist:{jti}` | Token remaining validity | Auth Service / Gateway | Revoked token JTI |
| Refresh Token | `refresh:{userId}` | 7 days | Auth Service | Refresh token JTI |
| OTP | `otp:{mobile}` | 5 min | Auth Service / Customer | One-time password |
| Idempotency | `idempotency:{key}` | 24 hours | Transaction / UPI Service | Request → Response mapping |
| Rate Limit | `ratelimit:{userId}:{window}` | 1 min | API Gateway | Request count |
| Customer Profile | `customer:{customerId}` | 10 min | Customer Service | Customer details |
| Blacklisted Accounts | `blacklist:accounts` | 5 min | Fraud Service | Set of account IDs |
| Daily UPI Limit | `upi:limit:{upiId}:{date}` | 24 hours | UPI Service | Amount used today |

---

## 11. Database Architecture

```mermaid
graph TB
    subgraph PostgreSQL Cluster
        PG_P[(Primary<br/>Writes)]
        PG_R1[(Replica 1<br/>Reads)]
        PG_R2[(Replica 2<br/>Reads)]
        PG_P -->|Streaming Replication| PG_R1
        PG_P -->|Streaming Replication| PG_R2
    end

    subgraph Services using Primary Write
        TXN_W[Transaction Service<br/>writes]
        ACC_W[Account Service<br/>writes]
        CUST_W[Customer Service<br/>writes]
    end

    subgraph Services using Replica Read
        TXN_R[Transaction Service<br/>history queries]
        STMT_R[Statement Service<br/>statement generation]
        ADMIN_R[Admin Service<br/>reporting]
    end

    TXN_W & ACC_W & CUST_W --> PG_P
    TXN_R & STMT_R & ADMIN_R --> PG_R1
```

### Database Isolation Strategy
Each microservice owns its own **schema** within the shared PostgreSQL cluster (in development). In production, each service gets its own database instance for full isolation.

| Service | Schema (Dev) | Database (Prod) |
|---------|-------------|-----------------|
| Auth | `auth` | `auth_db` |
| Customer | `customer` | `customer_db` |
| Account | `account` | `account_db` |
| Transaction | `transaction` | `transaction_db` |
| Beneficiary | `beneficiary` | `beneficiary_db` |
| UPI | `upi` | `upi_db` |
| Fraud | `fraud` | `fraud_db` |
| Notification | `notification` | `notification_db` |
| Audit | `audit` | `audit_db` |

---

## 12. Network Architecture

```mermaid
graph TB
    subgraph Public Zone
        INTERNET[Internet]
        LB[Load Balancer<br/>TLS Termination]
    end

    subgraph DMZ - API Layer
        GW1[API Gateway Instance 1]
        GW2[API Gateway Instance 2]
    end

    subgraph Private Zone - Services
        SVC[Microservices<br/>8081-8091]
    end

    subgraph Private Zone - Infrastructure
        KAFKA[Kafka Cluster]
        REDIS[Redis Cluster]
        PG[PostgreSQL Cluster]
        MINIO[MinIO]
    end

    subgraph Management Zone
        PROM[Prometheus]
        GRAF[Grafana]
        KIBANA[Kibana]
        EUREKA[Eureka]
        CONFIG[Config Server]
    end

    INTERNET --> LB
    LB --> GW1 & GW2
    GW1 & GW2 --> SVC
    SVC --> KAFKA & REDIS & PG & MINIO
    SVC --> EUREKA & CONFIG
    PROM --> SVC
    GRAF --> PROM
```

---

## 13. Scaling Strategy

### Horizontal Scaling

| Service | Scaling Trigger | Min Instances | Max Instances |
|---------|----------------|---------------|---------------|
| API Gateway | CPU > 70% | 2 | 10 |
| Transaction Service | Queue depth > 1000 | 2 | 20 |
| Account Service | CPU > 60% | 2 | 10 |
| Notification Service | Kafka lag > 5000 | 1 | 5 |
| Fraud Detection | Kafka lag > 1000 | 2 | 8 |
| Auth Service | RPS > 5000 | 2 | 8 |
| Customer Service | CPU > 70% | 2 | 6 |

### Database Scaling
- **Primary** handles all writes
- **Replicas** handle all reads (transaction history, statements, admin queries)
- **Connection pooling** via HikariCP (max 20 connections per service instance)
- **Future**: Citus PostgreSQL for horizontal sharding by account_id

### Kafka Scaling
- 3 brokers for high availability
- Topics partitioned by account ID (ensures ordering per account)
- Consumer group scaling: add consumers up to number of partitions

---

## 14. High Availability

```mermaid
graph LR
    subgraph Zone A
        GW_A[Gateway A]
        SVC_A[Services A]
        PG_A[(PG Primary)]
        KF_A[Kafka Broker 1]
        KF_B[Kafka Broker 2]
    end

    subgraph Zone B
        GW_B[Gateway B]
        SVC_B[Services B]
        PG_B[(PG Replica)]
        KF_C[Kafka Broker 3]
    end

    LB[Load Balancer] --> GW_A & GW_B
    PG_A -->|Replication| PG_B
    KF_A & KF_B & KF_C -->|Replication| KF_A
```

- **No single point of failure** — every component runs at least 2 instances
- **Kafka replication factor: 3** — data survives 2 broker failures
- **PostgreSQL streaming replication** — replica promoted in < 30 seconds on primary failure
- **Redis Cluster** — 3 master + 3 replica nodes; automatic failover

---

## 15. Fault Tolerance

| Component | Failure Mode | Recovery Mechanism |
|-----------|-------------|-------------------|
| API Gateway down | Client gets 502 | Load balancer routes to other gateway instance |
| Transaction Service down | Request fails | Client retries with same idempotency key; processing completes on recovery |
| Kafka broker down | Message publication fails | Outbox poller retries; Kafka replication ensures no data loss |
| PostgreSQL primary down | Write fails | Auto-failover to replica (< 30 sec); Flyway migrations re-applied |
| Redis down | Cache miss | Services fall back to database reads; no functional failure |
| Notification Service down | Notification delayed | Kafka retains event; notification sent when service recovers |
| Fraud Service down | Fraud check skipped | Configurable: block all transactions OR allow with post-hoc review |

### Circuit Breaker Configuration (Resilience4j)

```
Sliding window: 10 requests
Failure rate threshold: 50%
Wait duration in OPEN state: 30 seconds
Permitted calls in HALF_OPEN: 3
```

---

## 16. Disaster Recovery

| Scenario | RPO | RTO | Strategy |
|---------|-----|-----|----------|
| Single service crash | 0 (event-driven) | < 60 sec | Kubernetes restarts pod; Kafka replays events |
| Database primary failure | < 5 sec (replication lag) | < 30 sec | Automatic failover to replica |
| Kafka cluster failure | 0 (Outbox Pattern) | < 5 min | Restore from replicas; Outbox replays unpublished events |
| Data center failure | < 60 sec | < 15 min | Multi-AZ deployment; DNS failover |
| Accidental data deletion | 24 hours (backup) | < 2 hours | Restore from daily PostgreSQL backup (PITR) |

### Backup Strategy
- **PostgreSQL**: Daily full backup + WAL archiving (Point-In-Time Recovery)
- **Kafka**: 7-day message retention (replay on consumer failure)
- **Redis**: RDB snapshots every hour + AOF for critical data (tokens, OTPs)

---

## 17. Deployment Diagram

```mermaid
graph TB
    subgraph Docker Compose - Development
        direction TB
        DC_GW[banking-gateway:8080]
        DC_AUTH[banking-auth:8081]
        DC_CUST[banking-customer:8082]
        DC_ACC[banking-account:8083]
        DC_TXN[banking-transaction:8084]
        DC_PG[postgres:5432]
        DC_REDIS[redis:6379]
        DC_KAFKA[kafka:9092]
        DC_ZK[zookeeper:2181]
        DC_MINIO[minio:9000]
        DC_EUREKA[eureka:8761]
    end

    subgraph Production - Future Kubernetes
        K8S_GW[Gateway Deployment<br/>2-10 replicas]
        K8S_TXN[Transaction Deployment<br/>2-20 replicas]
        K8S_PG[RDS PostgreSQL<br/>Multi-AZ]
        K8S_REDIS[ElastiCache Redis<br/>Cluster Mode]
        K8S_KAFKA[MSK Kafka<br/>3 brokers]
        K8S_INGRESS[Ingress Controller<br/>ALB]
    end
```

---

> **Next:** [Low Level Design →](04-LLD.md)
