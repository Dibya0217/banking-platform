# 02 — Architecture Decisions

> **Navigation:** [← Project Overview](01-Project-Overview.md) | [HLD →](03-HLD.md)

Each decision is documented in **Architecture Decision Record (ADR)** format:
- **Decision** — What was chosen
- **Why** — Rationale
- **Why Not** — Rejected alternatives and their trade-offs
- **Advantages** — Benefits of the chosen option
- **Disadvantages** — Known downsides
- **Trade-offs** — Accepted compromises

---

## Table of Contents

1. [ADR-001: Java 21](#adr-001-java-21)
2. [ADR-002: Spring Boot](#adr-002-spring-boot)
3. [ADR-003: Microservices over Monolith](#adr-003-microservices-over-monolith)
4. [ADR-004: PostgreSQL over MySQL](#adr-004-postgresql-over-mysql)
5. [ADR-005: Kafka over RabbitMQ](#adr-005-kafka-over-rabbitmq)
6. [ADR-006: Redis over Hazelcast](#adr-006-redis-over-hazelcast)
7. [ADR-007: REST over GraphQL](#adr-007-rest-over-graphql)
8. [ADR-008: JWT for Authentication](#adr-008-jwt-for-authentication)
9. [ADR-009: Flyway over Liquibase](#adr-009-flyway-over-liquibase)
10. [ADR-010: Maven over Gradle](#adr-010-maven-over-gradle)
11. [ADR-011: Docker and Docker Compose](#adr-011-docker-and-docker-compose)
12. [ADR-012: Event-Driven Architecture](#adr-012-event-driven-architecture)
13. [ADR-013: Saga Pattern (Choreography)](#adr-013-saga-pattern-choreography)
14. [ADR-014: Outbox Pattern](#adr-014-outbox-pattern)
15. [ADR-015: Optimistic Locking over Pessimistic](#adr-015-optimistic-locking-over-pessimistic)
16. [ADR-016: CQRS for Read-Heavy Services](#adr-016-cqrs-for-read-heavy-services)
17. [ADR-017: API Gateway](#adr-017-api-gateway)
18. [ADR-018: Idempotency Keys](#adr-018-idempotency-keys)

---

## ADR-001: Java 21

**Decision:** Java 21 (LTS)

### Why
- Long-Term Support until 2031 — enterprise stability requirement
- **Virtual Threads (Project Loom)** — structured concurrency without reactive complexity; ideal for I/O-bound banking APIs
- **Sealed Classes** — model domain states (AccountStatus, TransactionStatus) exhaustively
- **Pattern Matching** — cleaner instanceof handling in business logic
- **Record Classes** — immutable DTOs and value objects with zero boilerplate
- Java remains the dominant language in banking/fintech (used by JP Morgan, Goldman Sachs, SWIFT)

### Why Not Java 17
- Java 21 virtual threads replace the need for WebFlux reactive programming while still delivering high concurrency — simpler code, same throughput
- Java 17 requires reactive/non-blocking explicitly; Java 21 achieves this transparently

### Why Not Kotlin
- Interoperability overhead in large enterprise teams
- Java is universally understood across banking interview contexts
- Java 21 features close most ergonomic gaps with Kotlin

### Why Not Go / Node.js
- Spring ecosystem (Security, Data JPA, Actuator) has no equivalent maturity in Go/Node for banking
- Java's type system and null safety (with IDE tooling) reduce production bugs in financial code

**Advantages:** LTS stability, virtual threads, mature Spring ecosystem, universal team knowledge  
**Disadvantages:** JVM startup time (mitigated by Spring Boot native in future), verbose syntax  
**Trade-offs:** Accept verbosity in exchange for ecosystem maturity and enterprise adoption

---

## ADR-002: Spring Boot

**Decision:** Spring Boot 4.x

### Why
- **Auto-configuration** — zero-boilerplate setup for Security, JPA, Actuator, Kafka, Redis
- **Spring Security** — battle-tested JWT, RBAC, CORS, CSRF in banking context
- **Spring Data JPA** — repository pattern with Hibernate without manual SQL for CRUD
- **Spring Actuator** — `/health`, `/metrics`, `/info` out of the box for Kubernetes readiness
- **Spring Cloud** — Gateway, Config, Eureka all in the same ecosystem
- Dominant framework in Indian banking tech (HDFC, ICICI, Paytm all use Spring internally)

### Why Not Quarkus / Micronaut
- Smaller ecosystems; fewer battle-tested plugins for banking use cases
- Team ramp-up time is higher
- Spring Boot 3+ with GraalVM native images closes the startup-time gap

### Why Not Jakarta EE / WildFly
- Heavyweight deployment model; Docker footprint is larger
- Spring Boot's embedded server model is superior for microservice deployment

**Advantages:** Massive ecosystem, convention over configuration, strong community, Kubernetes-ready  
**Disadvantages:** Opinionated; magic auto-configuration can obscure behavior  
**Trade-offs:** Accept Spring's opinionated approach; use `@ConditionalOnProperty` to override defaults

---

## ADR-003: Microservices over Monolith

**Decision:** Microservice Architecture from Day 1

### Why
- **Independent Scaling** — Transaction Service scales to 10,000 TPS while Admin Service runs on minimal resources
- **Fault Isolation** — Notification Service outage does not block fund transfers
- **Independent Deployment** — Auth Service can be hotfixed and redeployed without touching Transaction logic
- **Technology Flexibility** — Fraud Detection Service can adopt Python/ML in the future without affecting the platform
- **Team Autonomy** — Each service is owned by one team, enabling parallel development
- Banking regulations often require isolated audit boundaries between business functions

### Why Not Modular Monolith
- A modular monolith shares one database and one deployment unit; a single bug in any module causes full outage
- Cannot scale individual features independently — the whole application scales as one unit
- Database schema coupling grows over time even with module boundaries
- The user explicitly requested: *"Do NOT design this as a Modular Monolith"*

### Why Not Serverless (AWS Lambda)
- Cold start latency is unacceptable for real-time banking transactions
- Stateful connections (database pools, Kafka consumers) are difficult to manage in serverless
- Vendor lock-in conflicts with multi-cloud strategy

**Advantages:** Independent scale, fault isolation, team autonomy, technology diversity  
**Disadvantages:** Network latency, distributed system complexity, operational overhead  
**Trade-offs:** Accept higher ops complexity in exchange for the scale and resilience that banking requires

---

## ADR-004: PostgreSQL over MySQL

**Decision:** PostgreSQL 16

### Why
- **Superior ACID guarantees** — true serializable isolation; critical for financial consistency
- **MVCC implementation** — PostgreSQL's MVCC reduces lock contention vs MySQL's gap locking
- **JSONB columns** — store flexible audit metadata and KYC documents without a schema migration
- **Advisory Locks** — application-level locking for idempotency without extra tables
- **Row-Level Security** — database-native multi-tenant isolation (future)
- **Full-Text Search** — native FTS for transaction descriptions without Elasticsearch for basic cases
- **Window Functions** — complex reporting queries (running balance, monthly summaries) are native SQL
- **`FOR UPDATE SKIP LOCKED`** — efficient queue-style processing for Outbox pattern
- PostgreSQL is used by Revolut, N26, and most modern digital banks

### Why Not MySQL
- MySQL's gap locking causes more deadlocks under high concurrency
- Less advanced JSON support (MySQL 5.7 JSON vs PostgreSQL JSONB with indexing)
- PostgreSQL's planner produces better query plans for complex analytical queries
- Weaker full-text search, no advisory locks natively

### Why Not Oracle DB
- Cost prohibitive for startups; licensing model conflicts with cloud-native scaling
- Vendor lock-in; PostgreSQL is portable across any cloud

### Why Not MongoDB
- Financial data requires ACID transactions across multiple collections — MongoDB multi-document transactions have higher overhead
- Schema enforcement is critical in banking; schema-less databases introduce data integrity risks
- Relational model perfectly represents account/transaction relationships

**Advantages:** ACID, MVCC, JSONB, advisory locks, window functions, cost-free  
**Disadvantages:** Vertical scaling ceiling; sharding is complex (Citus for future)  
**Trade-offs:** Accept sharding complexity in exchange for strong consistency guarantees

---

## ADR-005: Kafka over RabbitMQ

**Decision:** Apache Kafka 3.x

### Why
- **Log-based, replayable** — Kafka retains messages for configurable periods; consumer can replay from any offset; critical for financial event sourcing
- **Ordered partitions** — events for one account are guaranteed ordered within a partition (partition by account ID)
- **Consumer groups** — multiple independent consumers (Notification, Fraud, Audit) consume the same event without coordination
- **Throughput** — Kafka handles millions of messages per second; RabbitMQ peaks at ~50k/sec per queue
- **Exactly-once semantics** — Kafka supports idempotent producers and transactional consumers
- **Schema registry** — native integration with Confluent Schema Registry for Avro schema evolution
- Kafka is used by LinkedIn, Revolut, Uber Payments, and Stripe for event streaming

### Why Not RabbitMQ
- RabbitMQ deletes messages after consumption; no replay capability for regulatory audit
- Queue-based model requires explicit dead-letter queue setup per queue
- RabbitMQ is better for RPC-style request/reply; Kafka is superior for event streaming
- Throughput ceiling is lower under banking workloads

### Why Not AWS SQS/SNS
- Vendor lock-in; our platform must be cloud-portable
- SQS FIFO queues have throughput limits (3,000 msg/sec per queue)
- No replay capability (messages deleted after retention)

### Why Not ActiveMQ
- Legacy technology; limited modern tooling; Kafka has replaced it in most new systems

**Advantages:** Replay, ordering, high throughput, consumer groups, exactly-once semantics  
**Disadvantages:** Complex operational setup; ZooKeeper (or KRaft mode) overhead  
**Trade-offs:** Accept operational complexity in exchange for event replay and financial audit capability

---

## ADR-006: Redis over Hazelcast

**Decision:** Redis 7

### Why
- **Industry standard** — Redis is the most widely deployed in-memory store
- **Rich data structures** — Strings (OTP, sessions), Hashes (rate limit counters), Sorted Sets (leaderboards), Streams (append log), Lists (queues)
- **TTL per key** — simple, built-in expiry; no manual eviction code
- **Pub/Sub** — lightweight event broadcasting for session invalidation
- **Redisson** — mature Java client with distributed locks, semaphores, rate limiters
- **Redis Cluster** — horizontal partitioning for scale without application changes
- **Persistence** — RDB snapshots + AOF for durability when needed
- Used by Twitter, GitHub, Snapchat, and every major bank's caching layer

### Why Not Hazelcast
- Hazelcast embeds in the JVM; Redis is a standalone server — better operational separation
- Redis has a much larger operator community and better managed cloud offerings (ElastiCache, Redis Cloud)
- Hazelcast licensing changed (BSL); Redis Open Source license is stable for our use case

### Why Not Memcached
- Memcached is cache-only; Redis supports distributed locks, pub/sub, streams which we need
- Memcached lacks persistence; Redis AOF provides durability for OTP storage

### Why Not In-Memory (Caffeine/Guava)
- Local caches are per-instance; in a microservice cluster every instance has its own cache = stale data
- Distributed cache ensures all instances see the same state (e.g., blacklisted tokens)

**Advantages:** Rich data structures, TTL, cluster mode, Redisson client, wide adoption  
**Disadvantages:** Extra infrastructure component; memory-only by default (configure AOF for durability)  
**Trade-offs:** Accept memory cost in exchange for consistent distributed state

---

## ADR-007: REST over GraphQL

**Decision:** REST with OpenAPI 3.0

### Why
- **Simplicity** — REST is universally understood by all consuming teams (mobile, web, third-party)
- **Banking API conventions** — RBI, NPCI, Open Banking specifications are all REST-based
- **HTTP caching** — GET requests for balance/statement can be cached at CDN/API Gateway
- **Tooling maturity** — Swagger UI, Postman, curl all work natively with REST
- **Authorization** — Per-endpoint RBAC is straightforward with Spring Security
- **Idempotency** — Idempotency-Key header is a well-established REST convention (Stripe's model)
- **Audit** — HTTP method + URL + status code provides a natural audit dimension

### Why Not GraphQL
- GraphQL's flexible queries make per-field authorization complex — banking requires strict field-level access control
- Over-fetching is not a concern when our APIs are purpose-built (we control both client and server)
- GraphQL schemas are harder to version than URL-versioned REST endpoints
- Banking regulators understand REST; GraphQL audit trails are less standardized
- N+1 problem in GraphQL requires DataLoader complexity not justified for our use case

### Why Not gRPC
- gRPC is ideal for internal service-to-service communication (we use Kafka for async; REST for sync)
- Browser clients cannot consume gRPC without a gateway proxy (additional complexity)
- REST is the required interface for open banking third-party access

**Advantages:** Simplicity, cacheability, universal tooling, regulatory alignment  
**Disadvantages:** Over-fetching for complex views (mitigated by purpose-built endpoints)  
**Trade-offs:** Accept potential over-fetching in exchange for simplicity and regulatory compliance

---

## ADR-008: JWT for Authentication

**Decision:** JWT (JSON Web Token) with Redis-based revocation

### Why
- **Stateless** — API Gateway validates JWT without calling Auth Service on every request; reduces latency
- **Scalable** — No session affinity required; any gateway node can validate any token
- **Standard** — RFC 7519; Spring Security has native JWT support
- **Claims** — Embed userId, roles, accountIds in token; downstream services extract without DB lookup
- **Short-lived access tokens (15 min) + long-lived refresh tokens (7 days)** — balance security and UX

### Token Revocation Problem
JWT is stateless by design — a revoked token remains valid until expiry. We solve this with:
- **Redis token blacklist** — on logout/force-revoke, token JTI (JWT ID) is stored in Redis with TTL = remaining token validity
- API Gateway checks Redis blacklist on every request
- Adds ~1ms Redis lookup latency; acceptable for security

### Why Not Opaque Tokens (Session Tokens)
- Session tokens require a database/cache lookup on every request — latency bottleneck at scale
- Session state must be replicated across all Auth Service instances

### Why Not OAuth 2.0 (Authorization Code Flow)
- OAuth 2.0 is designed for third-party delegation — unnecessary complexity for our own mobile/web clients
- We will add OAuth 2.0 in v2 for Open Banking (third-party app access)

**Advantages:** Stateless, scalable, standard, embeds claims  
**Disadvantages:** Revocation requires Redis; token size larger than session ID  
**Trade-offs:** Accept 1ms Redis lookup on revocation list in exchange for stateless scalability

---

## ADR-009: Flyway over Liquibase

**Decision:** Flyway

### Why
- **SQL-first** — Flyway migration scripts are plain SQL; any DBA can read and review them
- **Simplicity** — single annotation `@EnableTransactionManagement` + migration folder; no XML/YAML required
- **Spring Boot auto-configuration** — Flyway auto-runs on startup; zero configuration needed
- **Versioned migrations** — `V1__init.sql`, `V2__add_kyc.sql` — clear, sequential history
- **Repair command** — `flyway:repair` handles failed migrations without manual DB intervention
- **Proven at scale** — Used by Revolut, N26, and Stripe for database schema management

### Why Not Liquibase
- Liquibase uses XML/YAML changelogs — more verbose, harder for SQL-only DBAs to review
- Liquibase changeset IDs can conflict in multi-developer environments
- Rollback in Liquibase requires explicit rollback scripts; Flyway's forward-only model is safer for financial databases (rollback SQL in banking can corrupt data)
- Liquibase enterprise features (diff, snapshot) are not needed in our pipeline

**Advantages:** SQL-first, simple, Spring Boot native, forward-only (safer for finance)  
**Disadvantages:** No built-in rollback (by design); must write compensating migrations  
**Trade-offs:** Accept no automatic rollback in exchange for simplicity and DBA-readable scripts

---

## ADR-010: Maven over Gradle

**Decision:** Apache Maven

### Why
- **Declarative** — `pom.xml` is explicit; what you see is what you get
- **Enterprise adoption** — Maven is standard in Indian banking tech (HDFC, ICICI internal stacks)
- **Predictable builds** — Maven's lifecycle is fixed; no custom task ordering surprises
- **Spring Boot parent POM** — Spring manages all dependency versions via `spring-boot-starter-parent`; minimal version conflicts
- **CI/CD compatibility** — Every CI system (Jenkins, GitHub Actions) has mature Maven support
- **Multi-module support** — Maven parent POM for managing all microservice modules

### Why Not Gradle
- Gradle's flexibility is also its weakness — custom tasks accumulate technical debt
- Gradle build scripts (Groovy/Kotlin DSL) have a steeper learning curve for new team members
- Gradle's incremental builds are faster, but Maven with `-T` parallel flag is sufficient for our modules
- Gradle is superior for Android; overkill for a pure server-side project

**Advantages:** Declarative, predictable, Spring-native, universally understood  
**Disadvantages:** Slower builds than Gradle incremental; XML verbosity  
**Trade-offs:** Accept verbose XML in exchange for predictability and enterprise toolchain compatibility

---

## ADR-011: Docker and Docker Compose

**Decision:** Docker for containers; Docker Compose for local development

### Why Docker
- **Environment parity** — dev, test, staging, production run identical images
- **Dependency isolation** — PostgreSQL 16, Redis 7, Kafka 3 run in containers; no local installation
- **Immutable deployments** — image tag = exact version deployed; no "works on my machine"
- **Industry standard** — every cloud provider (AWS ECS, GCP Cloud Run, Azure AKS) runs Docker images

### Why Docker Compose
- **Local dev simplicity** — `docker-compose up` starts all 13 services + infrastructure
- **Service dependency management** — `depends_on` with `healthcheck` starts services in correct order
- **Network isolation** — each environment gets its own Docker network
- **Volume management** — persistent PostgreSQL data across container restarts

### Why Not Kubernetes for Now
- Kubernetes adds significant operational complexity (Deployments, Services, Ingress, RBAC, etcd)
- For a development-phase project, Docker Compose provides 80% of the benefit at 10% of the cost
- Kubernetes is planned for v2 production deployment (see [Deployment](10-Deployment.md))

**Advantages:** Environment parity, isolation, reproducibility, cloud portability  
**Disadvantages:** Container overhead; image build time in CI  
**Trade-offs:** Accept build time overhead in exchange for deployment reproducibility

---

## ADR-012: Event-Driven Architecture

**Decision:** Asynchronous event-driven communication via Kafka for cross-service workflows

### Why
- **Decoupling** — Transaction Service publishes `transaction.completed`; it doesn't know (or care) that Notification, Fraud, and Audit consume it
- **Resilience** — If Notification Service is down, Kafka retains the event; delivery happens when it recovers
- **Scalability** — Each consumer scales independently based on its processing capacity
- **Auditability** — Kafka log is a natural, ordered, tamper-evident audit trail

### Synchronous vs Asynchronous Decision Matrix

| Scenario | Communication Style | Reason |
|---------|--------------------|----|
| Customer queries balance | REST (sync) | Immediate response required |
| Transfer triggers fraud check | Kafka (async) | Non-blocking; fraud alert sent after |
| Transfer triggers notification | Kafka (async) | Non-blocking; SMS/email is best-effort |
| Admin queries transaction history | REST (sync) | User waiting for response |
| Statement generation | Kafka (async) | Long-running; not user-facing in real time |
| Audit logging | Kafka (async) | Never block a transaction for audit write |

**Advantages:** Decoupling, resilience, independent scaling, natural audit trail  
**Disadvantages:** Eventual consistency; harder to debug async flows; message ordering complexity  
**Trade-offs:** Accept eventual consistency for cross-service workflows; maintain strong consistency within a single service

---

## ADR-013: Saga Pattern (Choreography)

**Decision:** Saga with Choreography (event-driven) over Orchestration (central coordinator)

### Why Saga
- Distributed transactions (Transfer: debit Account A, credit Account B) cannot use ACID across service boundaries
- 2PC (Two-Phase Commit) is blocking, slow, and a single point of failure
- Saga breaks the distributed transaction into a sequence of local transactions, each publishing an event

### Why Choreography over Orchestration
- **No central point of failure** — In orchestration, the orchestrator going down halts all sagas; in choreography, failure in one service only affects that step
- **Lower coupling** — Services react to events rather than being commanded by a central service
- **Natural Kafka fit** — Choreography maps directly to Kafka publish/subscribe

### Transfer Saga Flow (Choreography)

```
Transaction Service → publishes [transfer.initiated]
     Account Service (debit A) → publishes [debit.completed] OR [debit.failed]
          Account Service (credit B) → publishes [credit.completed] OR [credit.failed]
               Notification Service → publishes [notification.sent]
                    Audit Service → persists audit record
```

On failure at any step, a compensating transaction is published (e.g., `debit.reversed`).

**Advantages:** No central coordinator, Kafka-native, independent failure handling  
**Disadvantages:** Harder to visualize the full saga flow; no single place to monitor saga state  
**Trade-offs:** Accept observability complexity; mitigate with Kafka tracing and correlation IDs

---

## ADR-014: Outbox Pattern

**Decision:** Transactional Outbox for guaranteed event delivery

### The Problem
A service writes to its database AND publishes to Kafka. If the database write succeeds but Kafka publish fails (network error), the event is lost. Money moves but no notification is sent — a financial data inconsistency.

### The Solution
1. Service writes its domain record AND an outbox event in the **same local database transaction** (atomically)
2. A separate **Outbox Poller** reads unpublished events from the `outbox_events` table and publishes to Kafka
3. On successful Kafka publish, the event is marked `published = true`
4. Uses `SELECT FOR UPDATE SKIP LOCKED` to allow multiple poller instances without conflicts

### Why Not Direct Kafka Publish
- If Kafka is unavailable at the moment of the transaction, the event is lost
- The Outbox Pattern provides at-least-once delivery with database-transaction atomicity

**Advantages:** Guaranteed at-least-once delivery; atomic with business transaction  
**Disadvantages:** Extra `outbox_events` table; poller adds ~100ms latency for event delivery  
**Trade-offs:** Accept slight latency increase in exchange for guaranteed event delivery

---

## ADR-015: Optimistic Locking over Pessimistic

**Decision:** Optimistic Locking with JPA `@Version` column

### Why Optimistic Locking
- **Higher throughput** — Optimistic locking doesn't hold DB row locks; concurrent reads are unblocked
- **No deadlocks** — No locks to deadlock; failed optimistic updates simply retry
- **Suitable for banking** — Most transactions don't contend on the same account simultaneously
- JPA `@Version` field (auto-incremented integer) detects concurrent modifications; throws `OptimisticLockException` which is retried

### Why Not Pessimistic Locking
- `SELECT FOR UPDATE` holds row locks for the entire transaction duration
- Under high concurrency (10,000 TPS), row lock queuing becomes a bottleneck
- Lock timeout errors cascade under load; optimistic locking fails fast and retries

### When We Use Pessimistic Locking
- Admin freeze operations where we intentionally need to block concurrent access
- UPI PIN change (requires exclusive access)

**Advantages:** High throughput, no deadlocks, suitable for concurrent account updates  
**Disadvantages:** Retry logic required; contention causes failed writes that must be retried  
**Trade-offs:** Accept retry overhead in exchange for higher overall throughput

---

## ADR-016: CQRS for Read-Heavy Services

**Decision:** CQRS (Command Query Responsibility Segregation) on Transaction Service and Statement Service

### Why CQRS
- **Transaction history** is read 100x more than written; a dedicated read model (backed by PostgreSQL read replica) removes read pressure from the write database
- **Statement generation** queries aggregated transaction data — running this on the write DB blocks transaction writes
- Write model: normalized, ACID, strongly consistent
- Read model: denormalized, eventually consistent, optimized for query patterns (date ranges, amounts, pagination)

### Where We Apply CQRS

| Service | Write Side | Read Side |
|---------|-----------|----------|
| Transaction Service | PostgreSQL primary (ACID writes) | PostgreSQL read replica (transaction history) |
| Statement Service | N/A (consumer only) | PostgreSQL read replica + materialized views |
| Account Service | PostgreSQL primary (balance updates) | Redis (cached balance for reads) |

### Why Not CQRS Everywhere
- CQRS adds complexity (eventual consistency, sync lag). Apply only where read/write ratio justifies it
- Customer Service, Beneficiary Service: read/write ratio is low; single database is simpler

**Advantages:** Read scalability, query optimization, write protection  
**Disadvantages:** Eventual consistency for read model; sync lag must be managed  
**Trade-offs:** Accept read-model staleness (typically < 1 second) in exchange for read scalability

---

## ADR-017: API Gateway

**Decision:** Spring Cloud Gateway as the single entry point

### Why API Gateway
- **Single entry point** — All clients connect to one host; no client-side service discovery
- **JWT validation at edge** — Tokens validated once at gateway; downstream services trust gateway-forwarded identity
- **Rate limiting** — Per-user/IP rate limits enforced without reaching individual services
- **Routing** — Path-based routing to correct microservice (`/api/v1/accounts/**` → Account Service)
- **CORS** — Centralized CORS policy; not duplicated in every service
- **Request logging** — All incoming requests logged with correlation ID before dispatch
- **Circuit breaker** — Resilience4j at gateway level; if Account Service is down, return 503 immediately

### Why Spring Cloud Gateway over nginx/Kong
- Spring Cloud Gateway integrates with Spring Security, Spring Cloud LoadBalancer, and Resilience4j natively
- Java-based filters allow custom authentication logic without Lua scripting (Kong) or C modules (nginx)
- Kong requires a separate Postgres instance; Spring Cloud Gateway is a Spring Boot app

**Advantages:** Centralized auth, rate limiting, routing, CORS, circuit breaking  
**Disadvantages:** Single point of failure (mitigated with multiple gateway instances); additional network hop  
**Trade-offs:** Accept one extra network hop in exchange for centralized cross-cutting concerns

---

## ADR-018: Idempotency Keys

**Decision:** Client-provided `Idempotency-Key` header for all mutation APIs

### Why
- **Duplicate prevention** — Network retries (mobile client retries on timeout) must not create duplicate transactions
- **Standard practice** — Stripe, PayPal, and all major payment APIs require idempotency keys
- Implementation: key → response stored in Redis with 24-hour TTL; repeat request returns stored response without re-execution

### Implementation
1. Client sends `Idempotency-Key: <UUID>` header
2. Transaction Service checks Redis: `idempotency:{key}` exists?
3. If yes → return cached response (200/201)
4. If no → process request, store response in Redis, return response

**Advantages:** Duplicate prevention, safe retries, simple client implementation  
**Disadvantages:** Redis storage for every write request (24 hours); key must be globally unique  
**Trade-offs:** Accept Redis memory cost in exchange for transaction integrity under network failures

---

> **Next:** [High Level Design →](03-HLD.md)
