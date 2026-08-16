# Implementation Plan — Enterprise Banking Backend

> **Start Date:** 2026-08-16 (Today)  
> **Duration:** 4 Weeks (28 days)  
> **Format:** Daily tasks with clear deliverables and verification steps

---

## How to Read This Plan

- Each day has **Tasks** (what to build), **Deliverables** (what you can show), and **Verify** (how to confirm it works)
- Days are **8-hour work days** — adjust if part-time
- Mark each day ✅ when complete before moving to the next
- Architecture docs are in `/docs/` — refer to them while building
- Never skip a day's verification — broken foundations slow every subsequent day

---

## Legend

| Symbol | Meaning |
|--------|---------|
| 🔧 | Setup / Configuration |
| 💻 | Code to write |
| 🧪 | Test to write |
| 📋 | Documentation update |
| ✅ | Completion checkpoint |

---

## WEEK 1 — Foundation + Auth + Customer + Account

**Goal by end of Week 1:** A working multi-module project where you can register a customer, verify OTP, get a JWT, and create an account. PostgreSQL, Redis, and Kafka all running in Docker.

---

### Day 1 — 2026-08-16 (Saturday) · Project Restructure + Infrastructure

**Theme:** Convert the single-module project into a multi-module Maven setup and get all infrastructure running.

**Why first:** Every service depends on the shared libraries and the running infrastructure. Nothing can be built without this foundation.

#### Morning (4 hours)

**Task 1: Convert to Multi-Module Maven (2h)**
- Edit root `pom.xml` to become a parent POM with `<packaging>pom</packaging>`
- Create module directories:
  ```
  mkdir -p shared/banking-commons/src/main/java/com/banking/common/{dto,exception,filter,util,annotation}
  mkdir -p shared/banking-events/src/main/java/com/banking/events/{customer,account,transaction,fraud}
  mkdir -p services/auth-service/src/main/java/com/banking/auth
  mkdir -p services/customer-service/src/main/java/com/banking/customer
  mkdir -p services/account-service/src/main/java/com/banking/account
  ```
- Create `pom.xml` for each module (inherit from root parent)
- Add `<modules>` to root `pom.xml`

**Task 2: Write `banking-commons` shared library (2h)**

Files to create:
- `ApiResponse.java` — generic `{ success, data, error, timestamp, correlationId }`
- `ErrorResponse.java` — `{ code, message, details[] }`
- `BankingException.java` — base RuntimeException with errorCode field
- `EntityNotFoundException.java extends BankingException`
- `BusinessRuleException.java extends BankingException`
- `ValidationException.java extends BankingException`
- `CorrelationIdFilter.java` — adds `X-Correlation-Id` to MDC on every request
- `MaskingUtil.java` — mask mobile (98765XXXXX), account (XXXXXX1234), Aadhaar
- `@Idempotent` annotation (empty for now — AOP wired later)

#### Afternoon (4 hours)

**Task 3: Write `banking-events` shared library (1h)**

Files to create (simple POJOs with Lombok):
- `BaseEvent.java` — `eventId, eventType, eventVersion, producedAt, correlationId, producerService`
- `CustomerRegisteredEvent.java` — extends BaseEvent
- `CustomerKycApprovedEvent.java`
- `AccountDebitedEvent.java`
- `AccountCreditedEvent.java`
- `TransactionInitiatedEvent.java`
- `TransactionCompletedEvent.java`
- `FraudCheckPassedEvent.java`
- `FraudAlertRaisedEvent.java`

**Task 4: Docker Compose Setup (2h)**
- Create `infrastructure/docker/docker-compose.yml` with:
  - PostgreSQL 16 (port 5432)
  - Redis 7 (port 6379, with password)
  - Zookeeper + Kafka (port 9092)
  - MinIO (port 9000)
  - Prometheus (port 9090)
  - Grafana (port 3000)
- Create `.env.example` with all variable names
- Create your own `.env` (gitignored) with dev values
- Create `infrastructure/scripts/init-db.sql` — create schemas: `auth`, `customer`, `account`, `transaction`, `beneficiary`, `upi`, `fraud`, `notification`, `audit`
- Create `infrastructure/scripts/create-kafka-topics.sh`

**Task 5: Verify infrastructure (1h)**
```bash
cd infrastructure/docker
docker-compose up -d
docker-compose ps                     # All healthy
docker exec banking-postgres psql -U banking -c "\dn"   # See schemas
docker exec banking-kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

#### Deliverables
- [ ] Root `pom.xml` with 5 modules declared
- [ ] `banking-commons` compiles: `mvn compile -pl shared/banking-commons`
- [ ] `banking-events` compiles: `mvn compile -pl shared/banking-events`
- [ ] `docker-compose up` starts all services without errors

#### Verify
```bash
mvn compile                           # Full project compiles
curl http://localhost:9000/minio/health/live  # MinIO OK
redis-cli -h localhost -a yourpass ping      # PONG
```

---

### Day 2 — 2026-08-17 (Sunday) · Auth Service — Core Setup

**Theme:** Build the Auth Service skeleton: dependencies, security config, database migration, and the `UserCredential` entity.

**Why today:** Auth Service is depended on by every other service. JWT issuing must be complete before anything else can be tested end-to-end.

#### Morning (4 hours)

**Task 1: Auth Service `pom.xml` dependencies (30 min)**

Add to `services/auth-service/pom.xml`:
```xml
<dependencies>
  <dependency>banking-commons</dependency>
  <dependency>banking-events</dependency>
  <dependency>spring-boot-starter-web</dependency>
  <dependency>spring-boot-starter-security</dependency>
  <dependency>spring-boot-starter-data-jpa</dependency>
  <dependency>spring-boot-starter-validation</dependency>
  <dependency>spring-boot-starter-actuator</dependency>
  <dependency>flyway-core + flyway-database-postgresql</dependency>
  <dependency>postgresql driver</dependency>
  <dependency>spring-boot-starter-data-redis</dependency>
  <dependency>jjwt-api 0.12.x + jjwt-impl + jjwt-jackson</dependency>
  <dependency>lombok</dependency>
  <dependency>mapstruct</dependency>
  <!-- Test -->
  <dependency>spring-boot-starter-test</dependency>
  <dependency>testcontainers:postgresql</dependency>
  <dependency>testcontainers:junit-jupiter</dependency>
</dependencies>
```

**Task 2: Auth Service `application.yml` (30 min)**
```yaml
spring:
  application.name: auth-service
  datasource:
    url: jdbc:postgresql://localhost:5432/banking?currentSchema=auth
    username: banking
    password: ${POSTGRES_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
    show-sql: false
  flyway:
    schemas: auth
    locations: classpath:db/migration
  data.redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
    password: ${REDIS_PASSWORD}
server:
  port: 8081
jwt:
  secret: ${JWT_SECRET}
  access-token-expiry: 900
  refresh-token-expiry: 604800
```

**Task 3: Flyway Migration V1 — user_credentials table (30 min)**

File: `src/main/resources/db/migration/V1__init_auth_schema.sql`
```sql
CREATE TABLE user_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    mobile          VARCHAR(15)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_credentials_email  ON user_credentials(email);
CREATE INDEX idx_user_credentials_mobile ON user_credentials(mobile);
```

**Task 4: `UserCredential` JPA Entity (30 min)**

**Task 5: `SecurityConfig.java` (30 min)**
- Permit: `/actuator/health`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/otp/**`
- All other routes: authenticated
- Stateless session (STATELESS)
- CSRF disabled (stateless API)

**Task 6: `JwtConfig.java` + `@ConfigurationProperties` binding (30 min)**

**Task 7: `JwtUtil.java` (1h)**
- `generateAccessToken(userId, email, roles, accountIds)` → signed JWT
- `generateRefreshToken(userId)` → signed JWT
- `validateAndExtract(token)` → Claims or throw
- `extractJti(token)` → String

#### Afternoon (4 hours)

**Task 8: `TokenService.java` (1h)**
- `generateAccessToken(...)` → calls JwtUtil, returns token string
- `generateRefreshToken(...)` → calls JwtUtil
- `revokeToken(jti, remainingSeconds)` → `SET blacklist:{jti} 1 EX seconds` in Redis
- `isBlacklisted(jti)` → `EXISTS blacklist:{jti}` in Redis
- `storeRefreshToken(userId, jti)` → `SET auth:refresh:{userId} {jti} EX 604800`
- `verifyRefreshToken(userId, jti)` → compare stored JTI
- `deleteRefreshToken(userId)` → `DEL auth:refresh:{userId}`

**Task 9: `OtpService.java` (1h)**
- `generateOtp()` → 6-digit string (SecureRandom)
- `storeOtp(mobile, purpose, otp)` → Redis Hash TTL 5 min
- `verifyOtp(mobile, purpose, submitted)` → check stored, increment attempts, single-use delete
- `isRateLimited(mobile)` → check `auth:otp:attempts:{mobile}` > 5

**Task 10: `UserCredentialRepository.java` (15 min)**

**Task 11: `AuthServiceImpl.java` skeleton (1h)**
- `login(email, password)` → BCrypt verify → generate tokens → return LoginResponse
- Account lockout logic (failed_attempts tracking)
- `logout(jti, remainingMs)` → revoke token
- `refresh(refreshToken)` → validate, rotate, issue new access token

**Task 12: `AuthController.java` (45 min)**
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/otp/send`
- `POST /api/v1/auth/otp/verify`

#### Deliverables
- [ ] Auth Service starts: `mvn spring-boot:run -pl services/auth-service`
- [ ] Flyway runs migration on startup (check `flyway_schema_history`)
- [ ] `/actuator/health` returns `{ status: "UP" }`

#### Verify
```bash
curl -s http://localhost:8081/actuator/health | jq .status
# "UP"
```

---

### Day 3 — 2026-08-18 (Monday) · Auth Service — Complete + Test

**Theme:** Complete Auth Service with full login/logout/refresh/OTP flow. Write tests.

#### Morning (4 hours)

**Task 1: `GlobalExceptionHandler.java` for Auth Service (1h)**
- Handle `InvalidCredentialsException` → 401
- Handle `AccountLockedException` → 423
- Handle `TokenExpiredException` → 401
- Handle `ConstraintViolationException` (validation) → 400
- All use `ApiResponse.error(...)` from banking-commons

**Task 2: Request/Response DTOs (45 min)**
- `LoginRequest.java` — `@NotBlank email`, `@NotBlank password`
- `LoginResponse.java` — `accessToken, refreshToken, tokenType, expiresIn, userId`
- `RefreshRequest.java` — `@NotBlank refreshToken`
- `TokenResponse.java` — `accessToken, tokenType, expiresIn`
- `OtpSendRequest.java` — `@Pattern mobile`, `purpose` enum
- `OtpVerifyRequest.java` — `mobile, otp, purpose`

**Task 3: `CorrelationIdFilter.java` wired into Auth Service (30 min)**

**Task 4: Flyway V2 — password history table (30 min)**

**Task 5: Internal API for Customer Service (1h)**
- `POST /internal/credentials` — creates UserCredential (called by Customer Service during registration)
- Secured by internal network only (no JWT — add `X-Internal-Secret` header check)

**Task 6: Swagger/OpenAPI Config (30 min)**
- Add springdoc-openapi dependency
- `@OpenAPIDefinition` on main class

#### Afternoon (4 hours)

**Task 7: Unit Tests — `TokenServiceTest` (1h)**
```java
@Test void generateAccessToken_shouldContainExpectedClaims() { ... }
@Test void revokeToken_shouldAddToBlacklist() { ... }
@Test void isBlacklisted_shouldReturnTrueAfterRevoke() { ... }
@Test void refreshToken_shouldRotate_andInvalidateOld() { ... }
```

**Task 8: Unit Tests — `OtpServiceTest` (1h)**
```java
@Test void storeAndVerifyOtp_shouldSucceedOnFirstAttempt() { ... }
@Test void verifyOtp_shouldFailAfterMaxAttempts() { ... }
@Test void verifyOtp_shouldDeleteAfterSuccess() { ... }
```

**Task 9: Unit Tests — `AuthServiceImplTest` (1h)**
```java
@Test void login_withValidCredentials_shouldReturnTokens() { ... }
@Test void login_withWrongPassword_shouldIncrementFailedAttempts() { ... }
@Test void login_afterFiveFailures_shouldLockAccount() { ... }
@Test void logout_shouldBlacklistToken() { ... }
```

**Task 10: Integration Test — `AuthControllerTest` (1h)**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class AuthControllerIntegrationTest {
    // POST /login with real PostgreSQL + Redis
    // POST /refresh with real tokens
    // POST /logout then verify token rejected
}
```

#### Deliverables
- [ ] `POST /api/v1/auth/otp/send` works (OTP stored in Redis)
- [ ] `POST /api/v1/auth/login` returns JWT tokens
- [ ] `POST /api/v1/auth/refresh` returns new access token
- [ ] `POST /api/v1/auth/logout` blacklists token
- [ ] All tests pass: `mvn test -pl services/auth-service`
- [ ] Swagger UI accessible: `http://localhost:8081/swagger-ui.html`

#### Verify
```bash
# Send OTP
curl -X POST http://localhost:8081/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9876543210","purpose":"REGISTRATION"}'

# Login (after seeding a test user)
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test@1234"}'
```

---

### Day 4 — 2026-08-19 (Tuesday) · Customer Service — Entity + Registration

**Theme:** Build Customer Service: entity, Flyway migrations, registration API, KYC submission.

#### Morning (4 hours)

**Task 1: Customer Service project setup (30 min)**
- `pom.xml` with dependencies (same as Auth + Kafka)
- `application.yml` pointing to `customer` schema
- Copy `application.yml` structure from Auth

**Task 2: Flyway V1 — `customers` + `customer_kyc` tables (1h)**
- Full SQL from `05-Database-Design.md`
- Include all indexes

**Task 3: JPA Entities (1h)**
- `Customer.java` with all fields, `@Entity`, `@Table(schema="customer")`
- `CustomerKyc.java`
- `CustomerStatus.java` enum: `PENDING_VERIFICATION, PENDING_KYC, ACTIVE, FROZEN, CLOSED, KYC_REJECTED`
- `KycStatus.java` enum: `PENDING, APPROVED, REJECTED`
- `DocumentType.java` enum

**Task 4: Repositories (30 min)**
- `CustomerRepository.java` — `findByEmail`, `findByMobile`, `findByPanNumber`, `existsByEmailOrMobile`
- `CustomerKycRepository.java` — `findByCustomerIdAndStatus`

**Task 5: `CustomerMapper.java` (MapStruct) (1h)**
- `toResponse(Customer)` → `CustomerResponse`
- Mask mobile in response for CUSTOMER role

#### Afternoon (4 hours)

**Task 6: `CustomerRegistrationRequest.java` with full validation (30 min)**
- `@NotBlank @Size` on fullName
- `@Email` on email
- `@Pattern(regexp="^[6-9]\\d{9}$")` on mobile
- `@Pattern(regexp=passwordRegex)` on password
- `@Past @NotNull` on dateOfBirth — custom age validator (≥18)

**Task 7: `AgeValidator.java` — custom `ConstraintValidator` (30 min)**
- Validates `LocalDate` is at least 18 years before today

**Task 8: `CustomerServiceImpl.java` — `register()` method (1h)**
1. Check duplicate email/mobile → throw `DuplicateCustomerException`
2. BEGIN TRANSACTION
3. INSERT customer (status=PENDING_VERIFICATION)
4. Call Auth Service internal API to create UserCredential
5. COMMIT
6. Return `{ customerId, "OTP sent to mobile" }`

**Task 9: `CustomerController.java` (1h)**
- `POST /api/v1/customers/register` (public)
- `GET /api/v1/customers/{id}` (authenticated)
- `PUT /api/v1/customers/{id}` (authenticated, own only)
- `POST /api/v1/customers/{id}/kyc` (authenticated, multipart)
- `POST /api/v1/customers/{id}/freeze` (ADMIN only)

**Task 10: `SecurityConfig.java` for Customer Service (30 min)**
- Extract userId from `X-User-Id` header (set by Gateway)
- In dev/test: use a simple filter that trusts the header directly
- `@PreAuthorize` on controller methods

**Task 11: `GlobalExceptionHandler.java` (30 min)**
- `DuplicateCustomerException` → 409
- `CustomerNotFoundException` → 404
- `UnderAgeException` → 400

#### Deliverables
- [ ] Customer Service starts on port 8082
- [ ] Flyway creates `customers` and `customer_kyc` tables
- [ ] `POST /api/v1/customers/register` inserts customer record

#### Verify
```bash
curl -X POST http://localhost:8082/api/v1/customers/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Priya Sharma",
    "email": "priya@example.com",
    "mobile": "9876543210",
    "password": "Test@1234",
    "dateOfBirth": "1990-05-15"
  }'
# Returns: { "customerId": "uuid", "status": "PENDING_VERIFICATION" }
```

---

### Day 5 — 2026-08-20 (Wednesday) · Customer Service — KYC + Events + Tests

**Theme:** Complete KYC flow, publish Kafka events, write tests.

#### Morning (4 hours)

**Task 1: Kafka Producer Config for Customer Service (30 min)**
- Add Kafka dependency to `pom.xml`
- `KafkaProducerConfig.java` — configure `KafkaTemplate<String, Object>`

**Task 2: `CustomerEventPublisher.java` (1h)**
- `publishCustomerRegistered(CustomerRegisteredEvent)` → topic: `banking.customer.events`, key: customerId
- `publishCustomerKycApproved(CustomerKycApprovedEvent)` → same topic
- `publishCustomerFrozen(CustomerFrozenEvent)`
- All events wrapped with BaseEvent fields (eventId, producedAt, correlationId from MDC)

**Task 3: `OutboxEvent.java` entity + `OutboxRepository` (30 min)**

**Task 4: `OutboxPoller.java` (1h)**
- `@Scheduled(fixedDelay = 100)` — poll every 100ms
- `SELECT * FROM outbox_events WHERE published = false ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED`
- Publish each event to Kafka via `KafkaTemplate.send(...)`
- `UPDATE published = true, published_at = NOW()`

**Task 5: Update `CustomerServiceImpl` to use Outbox (30 min)**
- In the same transaction as customer INSERT, also INSERT into `outbox_events`
- Remove direct Kafka publish (let Outbox Poller handle it)

**Task 6: Flyway V2 — `outbox_events` table (30 min)**

#### Afternoon (4 hours)

**Task 7: KYC Service Implementation (1h)**
- `KycService.submitKyc(customerId, documents)` — saves `customer_kyc` record with status=PENDING
- Document URL: in dev, use local file path; in prod, S3 upload (stub it for now)
- `KycService.approveKyc(customerId, kycId, adminId)` — update status=APPROVED, publish `customer.kyc.approved`
- `KycService.rejectKyc(customerId, kycId, reason, adminId)` — update status=REJECTED, publish event

**Task 8: OTP Verification Webhook (30 min)**
- `POST /api/v1/customers/{id}/verify-mobile` — called after OTP verify in Auth; updates status to PENDING_KYC

**Task 9: Customer Service Unit Tests (1h)**
```java
@Test void register_withValidRequest_shouldCreateCustomer() { ... }
@Test void register_withDuplicateEmail_shouldThrow409() { ... }
@Test void register_withUnderAge_shouldThrow400() { ... }
@Test void submitKyc_shouldCreateKycRecord() { ... }
```

**Task 10: Customer Service Integration Test (1h)**
- TestContainers PostgreSQL + Kafka
- Full registration flow end-to-end
- Verify `outbox_events` record created

#### Deliverables
- [ ] Registration flow: register → OTP verify → status becomes PENDING_KYC
- [ ] Kafka events published via Outbox Poller (verify in Kafka logs)
- [ ] KYC submission creates record in `customer_kyc`
- [ ] All tests pass

#### Verify
```bash
# Check Kafka event
docker exec banking-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic banking.customer.events --from-beginning
```

---

### Day 6 — 2026-08-21 (Thursday) · Account Service — Entity + Balance + Optimistic Lock

**Theme:** Build Account Service with balance management and optimistic locking.

#### Morning (4 hours)

**Task 1: Account Service setup + Flyway V1 (1h)**
- Create `accounts` table with `version` column (optimistic lock)
- Include `balance CHECK (balance >= 0)`

**Task 2: `Account.java` Entity (1h)**
```java
@Entity
@Table(name = "accounts", schema = "account")
public class Account {
    // All fields from DB Design doc
    @Version private long version;   // JPA optimistic locking

    public void debit(BigDecimal amount) {
        status.validateDebit(this, amount);   // State pattern
        this.balance = this.balance.subtract(amount);
    }
    public void credit(BigDecimal amount) {
        status.validateCredit(this);
        this.balance = this.balance.add(amount);
    }
}
```

**Task 3: `AccountStatus.java` enum with State pattern (1h)**
- Each enum value overrides `validateDebit()` and `validateCredit()`
- `ACTIVE` → allows both
- `FROZEN` → throws `AccountFrozenException` for both
- `CLOSED` → throws `AccountClosedException` for both

**Task 4: `AccountRepository.java` (30 min)**
- `findByAccountNumber(String)` → `Optional<Account>`
- `findByCustomerIdAndStatus(UUID, AccountStatus)` → `List<Account>`
- `existsByCustomerId(UUID)` → `boolean`

**Task 5: Account Number Generator (30 min)**
- Format: `YYYYMMDD` + 8 random digits
- Ensure uniqueness: retry on duplicate

#### Afternoon (4 hours)

**Task 6: `BalanceCacheService.java` (Redis) (1h)**
- `getCachedBalance(accountId)` → `Optional<BigDecimal>`
- `cacheBalance(accountId, balance, currency, status)` → Hash TTL 30s
- `invalidateBalance(accountId)` → `DEL balance:{accountId}`

**Task 7: `AccountServiceImpl.java` (2h)**
- `createAccount(request)` — validate customer active, max 3 accounts check, generate account number, save
- `getBalance(accountId)` — cache-aside pattern
- `debitAccount(accountId, amount, transactionId)` — with optimistic lock retry (max 3 times)
- `creditAccount(accountId, amount, transactionId)` — with optimistic lock retry
- `freezeAccount(accountId, reason, freezeType)` — update status, publish event
- `closeAccount(accountId)` — validate zero balance, close

**Task 8: `AccountController.java` + DTOs (1h)**
- `POST /api/v1/accounts`
- `GET /api/v1/accounts/{id}/balance`
- `GET /api/v1/accounts` (by customerId)
- `POST /api/v1/accounts/{id}/freeze` (ADMIN)
- `DELETE /api/v1/accounts/{id}`

#### Deliverables
- [ ] Account Service starts on port 8083
- [ ] `POST /api/v1/accounts` creates an account after customer KYC
- [ ] `GET /api/v1/accounts/{id}/balance` returns balance (Redis cached on second call)

#### Verify
```bash
# Create account (use customerId from Day 4)
TOKEN="<jwt from login>"
curl -X POST http://localhost:8083/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"uuid","accountType":"SAVINGS"}'

# Check balance
curl http://localhost:8083/api/v1/accounts/{id}/balance \
  -H "Authorization: Bearer $TOKEN"
```

---

### Day 7 — 2026-08-22 (Friday) · Account Service — Kafka Consumer + Tests + Week 1 Review

**Theme:** Account Service consumes Customer events. Write tests. Review and fix everything from Week 1.

#### Morning (4 hours)

**Task 1: Account Kafka Consumer — `CustomerEventConsumer.java` (1h)**
- Consume `banking.customer.events` topic
- On `customer.kyc.approved` → auto-create SAVINGS account
- Consumer group: `account-service-customer-cg`

**Task 2: Account Kafka Producer — `AccountEventPublisher.java` (1h)**
- `publishAccountCreated(AccountCreatedEvent)`
- `publishAccountDebited(AccountDebitedEvent)`
- `publishAccountCredited(AccountCreditedEvent)`
- All via Outbox Pattern

**Task 3: Outbox Poller for Account Service (30 min)**
- Same as Customer Service — copy and adapt

**Task 4: Account Service Tests (1.5h)**
```java
// Unit tests
@Test void debit_withSufficientFunds_shouldReduceBalance()
@Test void debit_withInsufficientFunds_shouldThrowException()
@Test void debit_onFrozenAccount_shouldThrowAccountFrozenException()
@Test void credit_onClosedAccount_shouldThrowAccountClosedException()
@Test void debit_withConcurrentUpdate_shouldRetryWithOptimisticLock()

// Integration tests with TestContainers
@Test void createAccount_shouldPublishAccountCreatedEvent()
@Test void getBalance_shouldReturnCachedValueOnSecondCall()
```

#### Afternoon (4 hours)

**Task 5: End-to-End Manual Test — Week 1 Full Flow (2h)**

Test this complete flow manually:
```
1. Start Auth, Customer, Account services
2. POST /auth/otp/send {mobile: "9999999999"}
3. POST /customers/register {fullName, email, mobile, password, dob}
4. POST /auth/login → get tokens
5. POST /customers/{id}/kyc → submit KYC
6. [Simulate admin approval] POST /admin/kyc/approve → triggers customer.kyc.approved event
7. [Account Service auto-creates account on kyc.approved event]
8. GET /accounts → see the auto-created account
9. GET /accounts/{id}/balance → see 0.00 balance
```

**Task 6: Fix all bugs found in Week 1 integration (2h)**
- Common issues: CORS, missing headers, DB constraint errors, Kafka not publishing

#### Week 1 Final Checklist
- [ ] All 3 services (Auth, Customer, Account) start without errors
- [ ] Full registration → KYC → account creation flow works
- [ ] JWT tokens issued and validated
- [ ] Redis caching working for balance
- [ ] Kafka events flowing between services
- [ ] All unit tests pass for all 3 services
- [ ] Docker Compose starts all infrastructure

---

## WEEK 2 — Transaction + Beneficiary + UPI Services

**Goal by end of Week 2:** Full money movement — deposit, withdraw, transfer between accounts, add beneficiaries, and UPI transfers.

---

### Day 8 — 2026-08-23 (Saturday) · Transaction Service — Foundation + Deposit/Withdraw

**Theme:** Build Transaction Service core. Implement deposit and withdrawal (simpler than transfer — no Saga needed).

#### Tasks
1. **Setup** — `pom.xml`, `application.yml` (schema: `transaction`), Flyway V1 (`transactions` + `outbox_events` tables)
2. **`Transaction.java` entity** — all fields, no `@Version` (transactions are append-only)
3. **`TransactionStatus` + `TransactionType` enums** (with State pattern for status transitions)
4. **`IdempotencyService.java`** — Redis-backed: check → process → store response
5. **`TransactionServiceImpl.deposit()`** — validate account active, call Account Service to credit, record transaction
6. **`TransactionServiceImpl.withdraw()`** — validate, call Account Service to debit, record transaction
7. **`TransactionController.java`** — `POST /deposit`, `POST /withdraw`
8. **Tests** — `@Test void deposit_shouldCreditAccount()`, `@Test void withdraw_withSufficientFunds_shouldDebitAccount()`

#### Verify
```bash
# Deposit
curl -X POST http://localhost:8084/api/v1/transactions/deposit \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accountId":"uuid","amount":10000,"description":"Initial deposit"}'

# Check balance jumped to 10000
curl http://localhost:8083/api/v1/accounts/{id}/balance -H "Authorization: Bearer $TOKEN"
```

---

### Day 9 — 2026-08-24 (Sunday) · Transaction Service — Transfer + Saga

**Theme:** Implement fund transfer using the Choreography Saga pattern. This is the most complex day — plan accordingly.

#### Tasks
1. **`TransferSagaCoordinator.java`** — publishes `transfer.initiated` event, tracks saga state
2. **`AccountEventConsumer.java`** — consumes `account.debited` → triggers credit; consumes `account.credited` → marks COMPLETED
3. **`FraudEventConsumer.java`** — consumes `fraud.check.passed` → allow debit; `fraud.alert.raised` (shouldBlock=true) → fail transaction
4. **`TransactionServiceImpl.transfer()`** — creates PENDING transaction + outbox event, returns 202
5. **`TransactionEventPublisher.java`** — publishes `transaction.initiated`, `transaction.completed`, `transaction.failed`
6. **Transaction Reversal** — `POST /transactions/{id}/reverse` (Admin only, T+1 rule)
7. **Transaction History** — `GET /transactions?accountId=...&page=0&size=20&sort=createdAt,desc` with Specification pattern for filtering

#### Verify
```bash
# Transfer 5000 from account A to account B
curl -X POST http://localhost:8084/api/v1/transactions/transfer \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"fromAccountId":"uuid1","toAccountId":"uuid2","amount":5000}'
# Returns 202

# Poll until completed
curl http://localhost:8084/api/v1/transactions/{txnId} -H "Authorization: Bearer $TOKEN"
# status: "COMPLETED"

# Verify balances
curl http://localhost:8083/api/v1/accounts/{uuid1}/balance
curl http://localhost:8083/api/v1/accounts/{uuid2}/balance
```

---

### Day 10 — 2026-08-25 (Monday) · Transaction Service — Tests + History

**Theme:** Write comprehensive tests for the Transaction Service. Implement paginated transaction history with filtering.

#### Tasks
1. **Unit Tests** — IdempotencyService, TransactionServiceImpl (all paths)
2. **Saga Tests** — test compensation on debit failure, on credit failure
3. **Integration Test** — full transfer saga with real Kafka + PostgreSQL via TestContainers
4. **Transaction History** — Specification-based filtering, pagination, sorting
5. **`GET /api/v1/transactions/{id}`** — with proper authorization (CUSTOMER sees own, ADMIN sees all)
6. **Duplicate detection** — test that same `Idempotency-Key` returns cached response

---

### Day 11 — 2026-08-26 (Tuesday) · Beneficiary Service

**Theme:** Build Beneficiary Service — add, verify (penny-drop), remove, cooldown enforcement.

#### Tasks
1. **Setup** — `pom.xml`, `application.yml`, Flyway V1 (`beneficiaries` table)
2. **`Beneficiary.java` entity** + `BeneficiaryStatus` enum
3. **`IFSCValidator.java`** — custom constraint validator for IFSC format `[A-Z]{4}0[A-Z0-9]{6}`
4. **`BeneficiaryServiceImpl.addBeneficiary()`** — validate IFSC, check max 20 limit, insert PENDING_VERIFICATION, trigger penny-drop
5. **`PennyDropService.java`** — in dev, simulate penny-drop success after 2 seconds; in prod, call UPI Service
6. **Cooldown enforcement** — `isTransferAllowed()` → check `transfer_enabled_at < now()`
7. **`BeneficiaryController.java`** — `POST /`, `DELETE /{id}`, `GET /`, `GET /{id}/verify`
8. **Tests** — max limit, cooldown, IFSC format validation

#### Verify
```bash
# Add beneficiary
curl -X POST http://localhost:8085/api/v1/beneficiaries \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accountNumber":"1234567890","ifscCode":"HDFC0001234","beneficiaryName":"Rahul"}'

# Check status
curl http://localhost:8085/api/v1/beneficiaries/{id}/verify -H "Authorization: Bearer $TOKEN"
```

---

### Day 12 — 2026-08-27 (Wednesday) · UPI Service

**Theme:** Build UPI Service — VPA management, PIN, transfers, daily limit.

#### Tasks
1. **Setup** — Flyway V1 (`upi_ids` + `upi_transactions` tables)
2. **`UpiId.java` entity** — VPA, pinHash (BCrypt), dailyLimit, status
3. **`UpiPinEncryptor.java`** — BCrypt hash for PIN storage
4. **`DailyLimitService.java`** — Redis `INCRBYFLOAT` with midnight TTL
5. **`UpiServiceImpl.createVpa()`** — validate format, unique VPA, hash PIN, save
6. **`UpiServiceImpl.changePin()`** — verify current PIN, hash new, save (with distributed lock)
7. **`UpiServiceImpl.transfer()`** — verify PIN, check daily limit, delegate to Transaction Service, record `upi_transaction`
8. **`UpiController.java`** — `POST /`, `PUT /{id}/pin`, `POST /transfer`, `GET /{id}/transactions`
9. **Tests** — PIN verification, daily limit enforcement

#### Verify
```bash
# Create UPI ID
curl -X POST http://localhost:8086/api/v1/upi \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accountId":"uuid","vpa":"priya@bank","pin":"123456"}'

# UPI Transfer
curl -X POST http://localhost:8086/api/v1/upi/transfer \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"payerVpa":"priya@bank","payeeVpa":"rahul@hdfc","amount":100,"pin":"123456"}'
```

---

### Day 13 — 2026-08-28 (Thursday) · Notification Service

**Theme:** Build Notification Service that consumes Kafka events and sends Email, SMS, Push.

#### Tasks
1. **Setup** — Flyway V1 (`notifications` + `notification_preferences` tables)
2. **`BaseNotificationSender.java`** — Template Method pattern (send → format → doSend → updateStatus)
3. **`EmailNotificationService.java`** — JavaMailSender (use MailHog for local dev: `docker run mailhog/mailhog`)
4. **`SmsNotificationService.java`** — Twilio (use Twilio test credentials or mock in dev)
5. **`PushNotificationService.java`** — FCM (stub in dev, real credentials in prod)
6. **`NotificationChannelFactory.java`** — Factory pattern
7. **All Kafka Consumers** — `TransactionEventConsumer`, `AccountEventConsumer`, `CustomerEventConsumer`
8. **Retry logic** — max 3 attempts, exponential backoff: 0s, 30s, 5min
9. **`FailedNotificationRetryScheduler.java`** — `@Scheduled` retries FAILED notifications every 10 minutes
10. **Tests** — mock email/SMS/push providers, verify retry logic

#### Verify
```bash
# After a transfer, check MailHog for email notification
open http://localhost:8025   # MailHog web UI
```

---

### Day 14 — 2026-08-29 (Friday) · Week 2 Review + Fraud Detection Service Start

**Theme:** End-to-end Week 2 testing + start Fraud Detection Service.

#### Morning (4 hours): Week 2 End-to-End Test

Test this full flow:
```
1. Register customer (Week 1 flow)
2. Deposit 50,000 into account
3. Add beneficiary (with penny-drop cooldown simulation)
4. After cooldown, transfer 5,000 to beneficiary
5. Create UPI ID
6. UPI transfer 500
7. Check notifications received in MailHog
8. View transaction history (pagination works)
9. Try duplicate transfer with same Idempotency-Key → same response returned
```

#### Afternoon (4 hours): Fraud Detection Service

1. **Setup** — Flyway V1 (`fraud_alerts` + `blacklisted_accounts` tables)
2. **`BlacklistCacheService.java`** — Redis Set, refreshed every 5 min by scheduler
3. **`VelocityCheckRule.java`** — Redis Sorted Set (`ZADD + ZCARD` per hour bucket)
4. **`BlacklistCheckRule.java`** — Redis Set lookup
5. **`LargeTransactionRule.java`** — threshold check
6. **`FraudRuleChain.java`** — Chain of Responsibility

---

## WEEK 3 — Fraud + Statement + Admin + Audit Services

**Goal by end of Week 3:** All 13 services running. Fraud detection blocking bad transactions. Statements generated as PDF. Admin can manage customers.

---

### Day 15 — 2026-08-30 (Saturday) · Fraud Detection Service — Complete

#### Tasks
1. **`FraudDetectionServiceImpl.java`** — consumes `transaction.initiated`, runs rule chain
2. **`FraudEventPublisher.java`** — publishes `fraud.check.passed` or `fraud.alert.raised`
3. **Auto-freeze logic** — if account has > 3 HIGH/CRITICAL alerts in 24h, publish `fraud.account.frozen`
4. **`FraudAlertRepository.java`** — `countByAccountIdAndSeverityInAndCreatedAtAfter()`
5. **Account Service Consumer** — consumes `fraud.account.frozen` → freeze account
6. **Tests** — velocity check fires at 11th transaction, blacklist blocks immediately

---

### Day 16 — 2026-08-31 (Sunday) · Statement Service

#### Tasks
1. **Setup** — Flyway V1 (`statements` table)
2. **`TransactionEventConsumer.java`** — builds read model (optional: can query read replica directly)
3. **`StatementServiceImpl.generateStatement(accountId, month, year)`** — query transactions from read replica, calculate opening/closing balance
4. **`PdfGenerationService.java`** — iText or Apache PDFBox, generate formatted PDF
5. **`MinioStorageService.java`** — upload PDF, return object key
6. **`MonthlyStatementScheduler.java`** — `@Scheduled(cron = "0 0 6 1 * *")` — 6 AM on 1st of month
7. **`StatementController.java`** — `GET /{accountId}/monthly`, `GET /{accountId}/download` (stream PDF)
8. **Tests** — PDF generation, MinIO upload

---

### Day 17 — 2026-09-01 (Monday) · Admin Service

#### Tasks
1. **Setup** — Admin Service reads from PostgreSQL read replica
2. **RBAC** — `ROLE_ADMIN`, `ROLE_OPS`, `ROLE_AUDITOR`, `ROLE_SUPER_ADMIN`
3. **`CustomerAdminController.java`** — `GET /customers` (paginated, searchable), `POST /customers/{id}/freeze`, `POST /customers/{id}/kyc/{kycId}/approve`, `POST /customers/{id}/kyc/{kycId}/reject`
4. **`AccountAdminController.java`** — `GET /accounts`, `POST /accounts/{id}/freeze`
5. **`TransactionAdminController.java`** — `GET /transactions` with full filter capability
6. **`FraudAdminController.java`** — `GET /fraud-alerts`, `POST /fraud-alerts/{id}/resolve`
7. **Admin-specific security** — IP allowlist, stricter rate limits

---

### Day 18 — 2026-09-02 (Tuesday) · Audit Service

#### Tasks
1. **Setup** — Flyway V1 (`audit_logs` table) — append-only
2. **`AllEventConsumer.java`** — subscribes to all topics (`banking.*.events` pattern)
3. **`AuditService.java`** — maps any domain event to `AuditLog` record
4. **DB user restriction** — create a separate DB user for Audit Service with only `INSERT + SELECT` on `audit_logs`
5. **`AuditController.java`** — `GET /api/v1/audit-logs` (AUDITOR + SUPER_ADMIN only), paginated, filterable
6. **Tests** — verify INSERT-only behavior, consumer covers all event types

---

### Day 19 — 2026-09-03 (Wednesday) · API Gateway

#### Tasks
1. **Setup** — New module `services/api-gateway`, Spring Cloud Gateway dependency
2. **Route Configuration** — `application.yml` routes for all 10 services
3. **`JwtAuthenticationFilter.java`** — validate JWT, check Redis blacklist, add X-User-Id header
4. **`RateLimitGatewayFilter.java`** — Redis-based sliding window
5. **`CorrelationIdGlobalFilter.java`** — add/propagate X-Correlation-Id
6. **`RequestLoggingFilter.java`** — log method, path, status, latency
7. **Security headers** — add HSTS, X-Frame-Options, etc. to all responses
8. **Circuit Breaker** — Resilience4j fallback for each service
9. **CORS configuration** — per allowed origins list

#### Verify
```bash
# Everything goes through port 8080 now
curl -X POST http://localhost:8080/api/v1/auth/login ...
curl http://localhost:8080/api/v1/accounts/{id}/balance -H "Authorization: Bearer $TOKEN"
```

---

### Day 20 — 2026-09-04 (Thursday) · Config + Eureka + Full Integration

#### Tasks
1. **Eureka Service** — `services/eureka-service` — standard Spring Cloud Netflix Eureka setup
2. **Config Service** — `services/config-service` — Spring Cloud Config Server pointing to a Git repo (or local filesystem)
3. **Register all services with Eureka** — add `spring-cloud-starter-netflix-eureka-client` to each service
4. **Centralized config** — move common properties to Config Service
5. **Update API Gateway** — use Eureka for service discovery instead of hardcoded hosts
6. **End-to-end test** — All 13 services running, all routes working via gateway

#### Verify
```bash
# Eureka dashboard
open http://localhost:8761
# Should show all 13 services registered
```

---

### Day 21 — 2026-09-05 (Friday) · Week 3 Review + Bug Fixes

**Full system end-to-end test:**

```
1. New customer registers
2. KYC submitted and approved by admin
3. Account auto-created
4. Deposit ₹50,000
5. Add beneficiary (cooldown 24h — simulate in test env by setting cooldown to 0)
6. Transfer ₹5,000 to beneficiary
7. Fraud check fires (velocity check doesn't trigger for 1 transaction)
8. Notification Service sends email (check MailHog)
9. Audit Service records all events
10. Create UPI ID
11. UPI transfer ₹100
12. Generate statement for current month
13. Download statement PDF
14. Admin views all transactions in Admin Service
15. Admin freezes account
16. Verify frozen account rejects new transactions
```

Fix all bugs discovered. Write missing tests.

---

## WEEK 4 — Testing + Monitoring + CI/CD + Documentation

**Goal by end of Week 4:** Production-ready: full test coverage, CI/CD pipeline, Prometheus/Grafana dashboards, Docker images built.

---

### Day 22 — 2026-09-06 (Saturday) · Integration Tests — All Services

#### Tasks
1. **Integration test suite** — TestContainers for each service
2. **Cross-service integration tests** — test the full Saga with real Kafka
3. **Test coverage** — aim for 80%+ line coverage
4. **`mvn test`** — all tests must pass across all modules
5. **Test containers reuse** — configure `testcontainers.reuse.enable=true` for faster test runs

---

### Day 23 — 2026-09-07 (Sunday) · Security Testing + Hardening

#### Tasks
1. **OWASP ZAP scan** — run against dev environment
2. **SQL injection test** — verify all endpoints reject SQL in inputs
3. **JWT tampering test** — verify modified JWT is rejected
4. **Rate limit test** — verify 429 after limit exceeded
5. **Authorization test** — verify customer cannot access other customer's data
6. **CORS test** — verify rejected origins get 403
7. **Input validation test** — all edge cases (empty, null, too long, special chars)
8. **Dependency vulnerability scan** — `mvn org.owasp:dependency-check-maven:check`
9. Fix all findings

---

### Day 24 — 2026-09-08 (Monday) · GitHub Actions CI/CD Pipeline

#### Tasks
1. **`.github/workflows/ci-cd.yml`** — full pipeline from `10-Deployment.md`
2. **Matrix build** — all services tested in parallel
3. **Docker image build** — multi-stage Dockerfile for each service
4. **Push to GHCR** — GitHub Container Registry
5. **Verify pipeline** — push a commit, watch Actions tab, verify all steps pass
6. **Add branch protection** — require CI to pass before merge to main

---

### Day 25 — 2026-09-09 (Tuesday) · Prometheus + Grafana Dashboards

#### Tasks
1. **`monitoring/prometheus.yml`** — scrape all services every 15s
2. **Custom metrics** — add to Transaction Service:
   - `banking.transactions.total` (counter, by type)
   - `banking.transactions.amount` (histogram)
   - `banking.fraud.alerts.total` (counter, by severity)
3. **Grafana dashboards** — import/create:
   - Banking Overview (TPS, error rate, p95 latency)
   - Transaction Service (success/fail rate, amounts)
   - Infrastructure (PostgreSQL, Redis, Kafka)
4. **Alert rules** — configure Prometheus alerting rules from `10-Deployment.md`
5. **Verify** — trigger a fraud alert, see it appear in Grafana

#### Verify
```bash
open http://localhost:3000   # Grafana
# Login: admin / yourpassword
# See all dashboards populated
```

---

### Day 26 — 2026-09-10 (Wednesday) · Logging + Structured JSON

#### Tasks
1. **`logback-spring.xml`** — structured JSON logging for all services
2. **ELK Stack** — add Elasticsearch + Logstash + Kibana to `docker-compose.yml`
3. **Logstash pipeline** — receive JSON from services, parse, forward to Elasticsearch
4. **Kibana index pattern** — `banking-logs-*`
5. **Kibana dashboards** — error rate by service, slow requests, fraud events
6. **PII masking** — verify mobile/account numbers masked in logs
7. **Correlation ID tracing** — search a correlationId in Kibana, see all log lines across services

#### Verify
```bash
open http://localhost:5601   # Kibana
# Search: correlationId:"some-uuid" — see logs from all services for one request
```

---

### Day 27 — 2026-09-11 (Thursday) · Performance + Load Testing

#### Tasks
1. **Load test tool** — Apache JMeter or k6
2. **Test scenarios**:
   - 100 concurrent users doing balance inquiry
   - 50 concurrent transfers (test optimistic lock retry)
   - 10 concurrent UPI transfers from same account (daily limit enforcement)
3. **Baseline metrics** — record p95 latency at different load levels
4. **Bottleneck identification** — fix any that exceed SLAs:
   - Balance inquiry: < 200ms p95
   - Transfer (202 Accepted): < 500ms p95
5. **HikariCP tuning** — adjust pool sizes based on test results
6. **Kafka consumer lag** — ensure < 5s under load

---

### Day 28 — 2026-09-12 (Friday) · Final Review + README + Project Polish

#### Tasks
1. **Root `README.md`** — project overview, quick-start instructions, architecture diagram
2. **Environment setup guide** — step-by-step: clone → configure `.env` → `docker-compose up` → run services
3. **API documentation** — verify all Swagger UIs accessible and complete
4. **Code cleanup** — remove all `System.out.println`, fix any TODO comments
5. **Final `mvn test`** — all 13 services, all tests pass
6. **Final `docker-compose up`** — clean startup from scratch
7. **Git tag** — `git tag v1.0.0`
8. **GitHub push** — clean main branch

#### Final Verification Checklist
```bash
# 1. Clean start
docker-compose down -v && docker-compose up -d

# 2. All services healthy
curl http://localhost:8080/actuator/health | jq .status   # Gateway
curl http://localhost:8081/actuator/health | jq .status   # Auth
curl http://localhost:8082/actuator/health | jq .status   # Customer
curl http://localhost:8083/actuator/health | jq .status   # Account
curl http://localhost:8084/actuator/health | jq .status   # Transaction
curl http://localhost:8085/actuator/health | jq .status   # Beneficiary
curl http://localhost:8086/actuator/health | jq .status   # UPI
curl http://localhost:8089/actuator/health | jq .status   # Notification
curl http://localhost:8090/actuator/health | jq .status   # Fraud
curl http://localhost:8091/actuator/health | jq .status   # Audit

# 3. All tests pass
mvn test

# 4. Docker images build
mvn package -DskipTests && docker-compose build

# 5. CI/CD passes
git push origin main   # Watch GitHub Actions
```

---

## Summary Calendar

| Week | Days | Services | Key Milestone |
|------|------|---------|--------------|
| Week 1 | Days 1-7 | Auth + Customer + Account | Full registration + JWT + balance working |
| Week 2 | Days 8-14 | Transaction + Beneficiary + UPI + Notification + Fraud (start) | Money movement end-to-end |
| Week 3 | Days 15-21 | Fraud + Statement + Admin + Audit + Gateway + Config + Eureka | All 13 services running |
| Week 4 | Days 22-28 | Testing + CI/CD + Monitoring + Performance | Production-ready |

---

## Daily Startup Checklist

Before starting each day:
```bash
# 1. Start infrastructure if not running
docker-compose up -d postgres redis kafka zookeeper minio

# 2. Verify infrastructure healthy
docker-compose ps

# 3. Check what you delivered yesterday
git log --oneline -5

# 4. Open today's section in this file
# 5. Mark tasks complete as you go
```

---

## If You Fall Behind

| Situation | What to Skip | What to Protect |
|-----------|-------------|----------------|
| 1 day behind | Test coverage, Skip load testing | Core functionality, Unit tests |
| 2 days behind | Statement Service PDF | Keep CSV; skip PDF generation |
| 3 days behind | Eureka + Config Service | Use hardcoded service URLs |
| 4+ days behind | Grafana dashboards | Prometheus metrics still needed |

**Never skip:** Auth Service tests, Transaction Saga tests, Fraud Detection — these are the interview-critical components.

---

*Plan created: 2026-08-16 | Target completion: 2026-09-12*
