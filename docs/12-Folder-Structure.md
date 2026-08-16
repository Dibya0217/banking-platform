# 12 — Folder Structure

> **Navigation:** [← UML Diagrams](11-UML-Diagrams.md) | [Design Patterns →](13-Design-Patterns.md)

---

## Table of Contents

1. [Repository Root Structure](#1-repository-root-structure)
2. [Per-Service Structure (Template)](#2-per-service-structure-template)
3. [Auth Service](#3-auth-service)
4. [Transaction Service](#4-transaction-service)
5. [Account Service](#5-account-service)
6. [Notification Service](#6-notification-service)
7. [Shared Libraries](#7-shared-libraries)
8. [Infrastructure Folder](#8-infrastructure-folder)
9. [Naming Conventions](#9-naming-conventions)

---

## 1. Repository Root Structure

```
banking-platform/
├── services/                          # All microservices
│   ├── api-gateway/
│   ├── auth-service/
│   ├── customer-service/
│   ├── account-service/
│   ├── transaction-service/
│   ├── beneficiary-service/
│   ├── upi-service/
│   ├── notification-service/
│   ├── fraud-detection-service/
│   ├── statement-service/
│   ├── admin-service/
│   ├── audit-service/
│   ├── config-service/
│   └── eureka-service/
│
├── shared/                            # Shared libraries (Maven modules)
│   ├── banking-commons/               # Common DTOs, exceptions, utilities
│   ├── banking-events/                # Kafka event schemas (POJOs)
│   └── banking-security/             # Shared JWT utilities
│
├── infrastructure/                    # Infrastructure as code
│   ├── docker/
│   │   └── docker-compose.yml
│   ├── kubernetes/                    # Future K8s manifests
│   │   ├── base/
│   │   └── overlays/
│   │       ├── staging/
│   │       └── production/
│   ├── monitoring/
│   │   ├── prometheus.yml
│   │   ├── alerting-rules/
│   │   └── grafana/
│   │       └── dashboards/
│   └── scripts/
│       ├── init-db.sql                # DB schema initialization
│       └── create-topics.sh           # Kafka topic creation
│
├── docs/                              # Architecture documentation (this folder)
│   ├── README.md
│   ├── 01-Project-Overview.md
│   ├── 02-Architecture-Decisions.md
│   └── ...
│
├── .github/
│   └── workflows/
│       ├── ci-cd.yml
│       ├── security-scan.yml
│       └── dependency-check.yml
│
├── pom.xml                            # Parent Maven POM
├── .env.example                       # Environment variable template
├── .gitignore
└── README.md
```

### Parent POM Structure
```xml
<!-- pom.xml (root) -->
<modules>
    <module>shared/banking-commons</module>
    <module>shared/banking-events</module>
    <module>shared/banking-security</module>
    <module>services/api-gateway</module>
    <module>services/auth-service</module>
    <module>services/customer-service</module>
    <module>services/account-service</module>
    <module>services/transaction-service</module>
    <module>services/beneficiary-service</module>
    <module>services/upi-service</module>
    <module>services/notification-service</module>
    <module>services/fraud-detection-service</module>
    <module>services/statement-service</module>
    <module>services/admin-service</module>
    <module>services/audit-service</module>
</modules>
```

---

## 2. Per-Service Structure (Template)

Every microservice follows this identical structure for consistency:

```
{service-name}/
├── pom.xml
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/banking/{service}/
│   │   │       ├── {ServiceName}Application.java      # Spring Boot entry point
│   │   │       │
│   │   │       ├── config/                            # Spring @Configuration classes
│   │   │       │   ├── SecurityConfig.java
│   │   │       │   ├── KafkaConfig.java
│   │   │       │   ├── RedisConfig.java
│   │   │       │   └── SwaggerConfig.java
│   │   │       │
│   │   │       ├── controller/                        # REST controllers (@RestController)
│   │   │       │   └── {Entity}Controller.java
│   │   │       │
│   │   │       ├── service/                           # Business logic
│   │   │       │   ├── {Entity}Service.java           # Interface
│   │   │       │   └── {Entity}ServiceImpl.java       # Implementation
│   │   │       │
│   │   │       ├── repository/                        # Spring Data JPA repositories
│   │   │       │   └── {Entity}Repository.java
│   │   │       │
│   │   │       ├── domain/                            # JPA entities and value objects
│   │   │       │   ├── {Entity}.java
│   │   │       │   └── enums/
│   │   │       │       └── {EntityStatus}.java
│   │   │       │
│   │   │       ├── dto/                               # Data Transfer Objects
│   │   │       │   ├── request/
│   │   │       │   │   └── {Action}Request.java
│   │   │       │   └── response/
│   │   │       │       └── {Entity}Response.java
│   │   │       │
│   │   │       ├── event/                             # Kafka producers and consumers
│   │   │       │   ├── {Entity}EventPublisher.java    # Kafka producer
│   │   │       │   ├── consumer/
│   │   │       │   │   └── {ExternalEntity}EventConsumer.java
│   │   │       │   └── events/                        # Event POJO classes
│   │   │       │       └── {Entity}{Action}Event.java
│   │   │       │
│   │   │       ├── outbox/                            # Outbox pattern
│   │   │       │   ├── OutboxEvent.java
│   │   │       │   ├── OutboxRepository.java
│   │   │       │   └── OutboxPoller.java
│   │   │       │
│   │   │       ├── mapper/                            # DTO ↔ Entity mapping (MapStruct)
│   │   │       │   └── {Entity}Mapper.java
│   │   │       │
│   │   │       ├── validator/                         # Custom validators
│   │   │       │   └── {Entity}Validator.java
│   │   │       │
│   │   │       ├── exception/                         # Custom exceptions + handler
│   │   │       │   ├── {Entity}NotFoundException.java
│   │   │       │   ├── BusinessRuleViolationException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       │
│   │   │       ├── filter/                            # Servlet filters
│   │   │       │   └── CorrelationIdFilter.java
│   │   │       │
│   │   │       ├── interceptor/                       # Spring interceptors
│   │   │       │   └── RequestLoggingInterceptor.java
│   │   │       │
│   │   │       ├── scheduler/                         # @Scheduled jobs
│   │   │       │   └── {Task}Scheduler.java
│   │   │       │
│   │   │       └── util/                              # Utility classes
│   │   │           └── {Context}Util.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                        # Default config
│   │       ├── application-dev.yml                    # Dev overrides
│   │       ├── application-test.yml                   # Test overrides
│   │       ├── logback-spring.xml                     # Structured JSON logging
│   │       └── db/
│   │           └── migration/                         # Flyway SQL migrations
│   │               ├── V1__init_schema.sql
│   │               ├── V2__add_indexes.sql
│   │               └── V3__seed_data.sql
│   │
│   └── test/
│       └── java/
│           └── com/banking/{service}/
│               ├── controller/
│               │   └── {Entity}ControllerTest.java    # @WebMvcTest (unit)
│               ├── service/
│   │               └── {Entity}ServiceTest.java       # @ExtendWith(MockitoExtension)
│               ├── repository/
│               │   └── {Entity}RepositoryTest.java    # @DataJpaTest
│               └── integration/
│                   └── {Entity}IntegrationTest.java   # @SpringBootTest + TestContainers
```

---

## 3. Auth Service

```
auth-service/
└── src/main/java/com/banking/auth/
    ├── AuthServiceApplication.java
    ├── config/
    │   ├── SecurityConfig.java          # Permit /login, /refresh, /otp/**
    │   ├── JwtConfig.java               # JWT properties binding
    │   └── RedisConfig.java
    ├── controller/
    │   └── AuthController.java          # /login, /logout, /refresh, /otp/send, /otp/verify
    ├── service/
    │   ├── AuthService.java
    │   ├── AuthServiceImpl.java
    │   ├── TokenService.java            # JWT creation, validation, revocation
    │   └── OtpService.java
    ├── repository/
    │   └── UserCredentialRepository.java
    ├── domain/
    │   ├── UserCredential.java
    │   └── enums/
    │       └── CredentialStatus.java    # ACTIVE, LOCKED, DISABLED
    ├── dto/
    │   ├── request/
    │   │   ├── LoginRequest.java
    │   │   ├── RefreshRequest.java
    │   │   └── OtpVerifyRequest.java
    │   └── response/
    │       ├── LoginResponse.java
    │       └── TokenResponse.java
    ├── exception/
    │   ├── InvalidCredentialsException.java
    │   ├── AccountLockedException.java
    │   ├── TokenExpiredException.java
    │   ├── TokenInvalidException.java
    │   └── GlobalExceptionHandler.java
    ├── filter/
    │   └── CorrelationIdFilter.java
    └── util/
        └── JwtUtil.java                 # sign, parse, extract, validate
```

---

## 4. Transaction Service

```
transaction-service/
└── src/main/java/com/banking/transaction/
    ├── TransactionServiceApplication.java
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── KafkaProducerConfig.java
    │   ├── KafkaConsumerConfig.java
    │   └── RedisConfig.java
    ├── controller/
    │   └── TransactionController.java   # /deposit, /withdraw, /transfer, /{id}, /history
    ├── service/
    │   ├── TransactionService.java
    │   ├── TransactionServiceImpl.java
    │   └── IdempotencyService.java
    ├── repository/
    │   ├── TransactionRepository.java   # Write replica
    │   └── TransactionReadRepository.java  # Read replica datasource
    ├── domain/
    │   ├── Transaction.java
    │   └── enums/
    │       ├── TransactionType.java
    │       └── TransactionStatus.java
    ├── dto/
    │   ├── request/
    │   │   ├── DepositRequest.java
    │   │   ├── WithdrawRequest.java
    │   │   └── TransferRequest.java
    │   └── response/
    │       ├── TransactionResponse.java
    │       └── TransactionHistoryResponse.java
    ├── event/
    │   ├── TransactionEventPublisher.java
    │   ├── consumer/
    │   │   ├── AccountEventConsumer.java    # account.debited, account.credited
    │   │   └── FraudEventConsumer.java      # fraud.check.passed, fraud.alert.raised
    │   └── events/
    │       ├── TransactionInitiatedEvent.java
    │       └── TransactionCompletedEvent.java
    ├── outbox/
    │   ├── OutboxEvent.java
    │   ├── OutboxRepository.java
    │   └── OutboxPoller.java
    ├── mapper/
    │   └── TransactionMapper.java
    ├── exception/
    │   ├── TransactionNotFoundException.java
    │   ├── DuplicateTransactionException.java
    │   ├── InsufficientFundsException.java
    │   └── GlobalExceptionHandler.java
    ├── filter/
    │   └── CorrelationIdFilter.java
    └── util/
        └── ReferenceNumberGenerator.java    # REFYYYYMMDDxxxxxx
```

---

## 5. Account Service

```
account-service/
└── src/main/java/com/banking/account/
    ├── AccountServiceApplication.java
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── KafkaConfig.java
    │   ├── RedisConfig.java
    │   └── DataSourceConfig.java        # Primary + Replica datasources
    ├── controller/
    │   └── AccountController.java       # POST /, GET /{id}/balance, POST /{id}/freeze, DELETE /{id}
    ├── service/
    │   ├── AccountService.java
    │   ├── AccountServiceImpl.java
    │   └── BalanceCacheService.java
    ├── repository/
    │   └── AccountRepository.java
    ├── domain/
    │   ├── Account.java                 # @Version for optimistic locking
    │   └── enums/
    │       ├── AccountType.java
    │       ├── AccountStatus.java
    │       └── FreezeType.java          # FULL, DEBIT_ONLY, CREDIT_ONLY
    ├── dto/
    │   ├── request/
    │   │   ├── CreateAccountRequest.java
    │   │   └── FreezeAccountRequest.java
    │   └── response/
    │       ├── AccountResponse.java
    │       └── BalanceResponse.java
    ├── event/
    │   ├── AccountEventPublisher.java
    │   ├── consumer/
    │   │   ├── CustomerEventConsumer.java   # customer.kyc.approved → create account
    │   │   ├── TransactionEventConsumer.java # transfer.initiated → debit/credit
    │   │   └── FraudEventConsumer.java       # fraud.account.frozen
    │   └── events/
    │       ├── AccountDebitedEvent.java
    │       └── AccountCreditedEvent.java
    ├── outbox/
    │   ├── OutboxEvent.java
    │   ├── OutboxRepository.java
    │   └── OutboxPoller.java
    ├── mapper/
    │   └── AccountMapper.java
    └── exception/
        ├── AccountNotFoundException.java
        ├── AccountFrozenException.java
        ├── InsufficientFundsException.java
        └── GlobalExceptionHandler.java
```

---

## 6. Notification Service

```
notification-service/
└── src/main/java/com/banking/notification/
    ├── NotificationServiceApplication.java
    ├── config/
    │   ├── KafkaConsumerConfig.java
    │   ├── MailConfig.java              # JavaMailSender configuration
    │   └── FirebaseConfig.java         # FCM initialization
    ├── consumer/
    │   ├── TransactionEventConsumer.java  # transaction.completed → SMS + email
    │   ├── AccountEventConsumer.java      # account.frozen → alert email
    │   ├── CustomerEventConsumer.java     # customer.registered → welcome email
    │   └── FraudEventConsumer.java        # fraud.alert → security alert
    ├── service/
    │   ├── NotificationService.java
    │   ├── EmailNotificationService.java
    │   ├── SmsNotificationService.java   # Twilio integration
    │   └── PushNotificationService.java  # Firebase FCM
    ├── repository/
    │   ├── NotificationRepository.java
    │   └── NotificationPreferenceRepository.java
    ├── domain/
    │   ├── Notification.java
    │   ├── NotificationPreference.java
    │   └── enums/
    │       ├── NotificationChannel.java  # EMAIL, SMS, PUSH
    │       └── NotificationStatus.java   # PENDING, SENT, FAILED, DEAD_LETTER
    ├── template/
    │   ├── EmailTemplateEngine.java      # Thymeleaf rendering
    │   └── TemplateType.java            # Enum of all email templates
    ├── retry/
    │   └── NotificationRetryHandler.java
    ├── scheduler/
    │   └── FailedNotificationRetryScheduler.java
    └── exception/
        └── NotificationDeliveryException.java
```

---

## 7. Shared Libraries

```
shared/
│
├── banking-commons/
│   └── src/main/java/com/banking/common/
│       ├── dto/
│       │   ├── ApiResponse.java           # Standard success response envelope
│       │   └── ErrorResponse.java         # Standard error response
│       ├── exception/
│       │   ├── BankingException.java      # Base exception
│       │   ├── EntityNotFoundException.java
│       │   ├── ValidationException.java
│       │   └── BusinessRuleException.java
│       ├── filter/
│       │   └── CorrelationIdFilter.java   # Shared across all services
│       ├── util/
│       │   ├── MaskingUtil.java           # mask mobile, account, Aadhaar
│       │   └── DateUtil.java
│       └── annotation/
│           └── Idempotent.java            # Custom annotation for idempotency AOP
│
├── banking-events/
│   └── src/main/java/com/banking/events/
│       ├── BaseEvent.java                 # eventId, eventType, producedAt, correlationId
│       ├── customer/
│       │   ├── CustomerRegisteredEvent.java
│       │   └── CustomerKycApprovedEvent.java
│       ├── account/
│       │   ├── AccountDebitedEvent.java
│       │   └── AccountCreditedEvent.java
│       ├── transaction/
│       │   ├── TransactionInitiatedEvent.java
│       │   └── TransactionCompletedEvent.java
│       └── fraud/
│           ├── FraudCheckPassedEvent.java
│           └── FraudAlertRaisedEvent.java
│
└── banking-security/
    └── src/main/java/com/banking/security/
        ├── JwtUtil.java                   # Shared JWT utilities
        ├── JwtProperties.java             # @ConfigurationProperties
        └── SecurityConstants.java         # Header names, role constants
```

---

## 8. Infrastructure Folder

```
infrastructure/
│
├── docker/
│   ├── docker-compose.yml              # Development
│   ├── docker-compose.test.yml         # CI testing
│   └── .env.example                    # Template for secrets
│
├── kubernetes/
│   ├── base/
│   │   ├── namespace.yaml
│   │   ├── configmap.yaml              # Non-secret config
│   │   └── services/
│   │       ├── transaction-service/
│   │       │   ├── deployment.yaml
│   │       │   ├── service.yaml
│   │       │   └── hpa.yaml
│   │       └── ...
│   └── overlays/
│       ├── staging/
│       │   └── kustomization.yaml      # Kustomize patches for staging
│       └── production/
│           └── kustomization.yaml      # Kustomize patches for prod
│
├── monitoring/
│   ├── prometheus.yml
│   ├── alerting-rules/
│   │   ├── banking-alerts.yml
│   │   └── infra-alerts.yml
│   └── grafana/
│       └── dashboards/
│           ├── banking-overview.json
│           ├── transaction-service.json
│           └── infrastructure.json
│
└── scripts/
    ├── init-db.sql                     # Create all schemas and users
    ├── create-kafka-topics.sh          # Create all topics with correct partitions
    ├── seed-roles.sql                  # Seed roles and permissions
    └── healthcheck.sh                  # Verify all services are healthy
```

---

## 9. Naming Conventions

### Java Classes
| Type | Convention | Example |
|------|-----------|---------|
| Entity | `PascalCase` | `Account`, `Transaction` |
| Service Interface | `{Entity}Service` | `AccountService` |
| Service Impl | `{Entity}ServiceImpl` | `AccountServiceImpl` |
| Repository | `{Entity}Repository` | `AccountRepository` |
| Controller | `{Entity}Controller` | `AccountController` |
| Request DTO | `{Action}Request` | `TransferRequest`, `CreateAccountRequest` |
| Response DTO | `{Entity}Response` | `AccountResponse`, `BalanceResponse` |
| Event | `{Entity}{Action}Event` | `AccountDebitedEvent` |
| Consumer | `{ExternalEntity}EventConsumer` | `CustomerEventConsumer` |
| Publisher | `{Entity}EventPublisher` | `TransactionEventPublisher` |
| Exception | `{Cause}Exception` | `InsufficientFundsException` |
| Mapper | `{Entity}Mapper` | `AccountMapper` |
| Scheduler | `{Task}Scheduler` | `OutboxPoller`, `StatementScheduler` |

### Database Objects
| Type | Convention | Example |
|------|-----------|---------|
| Table | `snake_case`, plural | `accounts`, `fraud_alerts` |
| Column | `snake_case` | `account_number`, `created_at` |
| Index | `idx_{table}_{column}` | `idx_accounts_customer_id` |
| Foreign Key | `fk_{table}_{referenced_table}` | `fk_accounts_customers` |
| Migration | `V{n}__{description}.sql` | `V3__add_fraud_alerts_index.sql` |

### Kafka Topics
```
banking.{entity}.{event-state}
banking.transaction.events
banking.account.events
banking.fraud.events
banking.transaction.events.dlq
```

### Docker Images
```
ghcr.io/{org}/banking/{service-name}:{tag}
ghcr.io/dibya0217/banking/transaction-service:1.0.0
ghcr.io/dibya0217/banking/transaction-service:latest
```

---

> **Next:** [Design Patterns →](13-Design-Patterns.md)
