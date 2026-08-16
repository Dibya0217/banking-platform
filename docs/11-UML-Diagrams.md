# 11 — UML Diagrams

> **Navigation:** [← Deployment](10-Deployment.md) | [Folder Structure →](12-Folder-Structure.md)

All diagrams use Mermaid syntax and render on GitHub, GitLab, and most modern documentation platforms.

---

## Table of Contents

1. [Class Diagram — Core Domain](#1-class-diagram--core-domain)
2. [Class Diagram — Transaction Saga](#2-class-diagram--transaction-saga)
3. [Sequence Diagram — Transfer Flow](#3-sequence-diagram--transfer-flow)
4. [Sequence Diagram — UPI Transfer](#4-sequence-diagram--upi-transfer)
5. [Sequence Diagram — Customer Registration](#5-sequence-diagram--customer-registration)
6. [Activity Diagram — Fraud Detection](#6-activity-diagram--fraud-detection)
7. [Activity Diagram — Statement Generation](#7-activity-diagram--statement-generation)
8. [State Diagram — Account Lifecycle](#8-state-diagram--account-lifecycle)
9. [State Diagram — Transaction Status](#9-state-diagram--transaction-status)
10. [State Diagram — Customer Lifecycle](#10-state-diagram--customer-lifecycle)
11. [Component Diagram](#11-component-diagram)
12. [Deployment Diagram](#12-deployment-diagram)

---

## 1. Class Diagram — Core Domain

```mermaid
classDiagram
    class Customer {
        +UUID id
        +String fullName
        +String email
        +String mobile
        +String panNumber
        +CustomerStatus status
        +LocalDate dateOfBirth
        +List~Account~ accounts
        +CustomerKyc kyc
        +register()
        +freeze(reason)
        +close()
        +submitKyc(documents)
    }

    class CustomerKyc {
        +UUID id
        +UUID customerId
        +DocumentType documentType
        +String documentNumber
        +String documentUrl
        +KycStatus status
        +approve(adminId)
        +reject(reason, adminId)
    }

    class Account {
        +UUID id
        +UUID customerId
        +String accountNumber
        +AccountType type
        +BigDecimal balance
        +AccountStatus status
        +long version
        +debit(amount) throws InsufficientFundsException
        +credit(amount)
        +freeze(reason, freezeType)
        +unfreeze()
        +close()
    }

    class Transaction {
        +UUID id
        +UUID fromAccountId
        +UUID toAccountId
        +TransactionType type
        +BigDecimal amount
        +TransactionStatus status
        +String idempotencyKey
        +String referenceNumber
        +UUID initiatedBy
        +markCompleted()
        +markFailed(reason)
        +reverse()
    }

    class Beneficiary {
        +UUID id
        +UUID customerId
        +String accountNumber
        +String ifscCode
        +String beneficiaryName
        +BeneficiaryStatus status
        +LocalDateTime transferEnabledAt
        +activate()
        +remove()
        +isTransferAllowed() bool
    }

    class UpiId {
        +UUID id
        +UUID customerId
        +UUID accountId
        +String vpa
        +String pinHash
        +BigDecimal dailyLimit
        +UpiStatus status
        +verifyPin(pin) bool
        +changePin(currentPin, newPin)
        +deactivate()
    }

    class FraudAlert {
        +UUID id
        +UUID accountId
        +UUID transactionId
        +String ruleTriggered
        +AlertSeverity severity
        +AlertStatus status
        +resolve(resolution, notes)
        +markFalsePositive()
    }

    Customer "1" --> "0..*" Account : has
    Customer "1" --> "0..1" CustomerKyc : has
    Customer "1" --> "0..*" Beneficiary : manages
    Customer "1" --> "0..*" UpiId : owns
    Account "1" --> "0..*" Transaction : from/to
    Transaction "1" --> "0..1" FraudAlert : triggers
    UpiId "1" --> "1" Account : linked to
```

---

## 2. Class Diagram — Transaction Saga

```mermaid
classDiagram
    class TransactionService {
        <<interface>>
        +initiateTransfer(request, idempotencyKey) TransactionResponse
        +deposit(request) TransactionResponse
        +withdraw(request) TransactionResponse
        +reverse(transactionId, reason) void
        +getTransaction(id) Transaction
        +getHistory(accountId, filter) Page~Transaction~
    }

    class TransactionServiceImpl {
        -transactionRepository: TransactionRepository
        -idempotencyService: IdempotencyService
        -eventPublisher: TransactionEventPublisher
        -outboxRepository: OutboxRepository
        +initiateTransfer(request, key) TransactionResponse
    }

    class IdempotencyService {
        -redisTemplate: StringRedisTemplate
        +isProcessed(key) boolean
        +storeResponse(key, response, ttl) void
        +getResponse(key) Optional~String~
    }

    class TransactionEventPublisher {
        -kafkaTemplate: KafkaTemplate
        +publishTransferInitiated(event) void
        +publishTransactionCompleted(event) void
        +publishTransactionFailed(event) void
    }

    class AccountEventConsumer {
        -transactionRepository: TransactionRepository
        -eventPublisher: TransactionEventPublisher
        +onAccountDebited(event) void
        +onAccountCredited(event) void
        +onDebitFailed(event) void
    }

    class FraudEventConsumer {
        -transactionRepository: TransactionRepository
        +onFraudCheckPassed(event) void
        +onFraudAlertRaised(event) void
    }

    class OutboxPoller {
        -outboxRepository: OutboxRepository
        -kafkaTemplate: KafkaTemplate
        +pollAndPublish() void
    }

    TransactionService <|.. TransactionServiceImpl
    TransactionServiceImpl --> IdempotencyService
    TransactionServiceImpl --> TransactionEventPublisher
    TransactionServiceImpl --> OutboxRepository
    AccountEventConsumer --> TransactionRepository
    FraudEventConsumer --> TransactionRepository
    OutboxPoller --> OutboxRepository
```

---

## 3. Sequence Diagram — Transfer Flow

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant TXN as Transaction Service
    participant Redis as Redis
    participant TXN_DB as Transaction DB
    participant OUTBOX as Outbox Events
    participant POLLER as Outbox Poller
    participant KAFKA as Kafka
    participant FRAUD as Fraud Detection
    participant ACC as Account Service
    participant ACC_DB as Account DB
    participant NOTIF as Notification Service

    Customer->>GW: POST /transfer + Idempotency-Key
    GW->>GW: Validate JWT
    GW->>TXN: Forward request

    TXN->>Redis: GET idempotency:{key} → nil
    TXN->>TXN_DB: BEGIN TXN
    TXN->>TXN_DB: INSERT transaction (PENDING)
    TXN->>OUTBOX: INSERT outbox_event (transfer.initiated)
    TXN->>TXN_DB: COMMIT
    TXN->>Redis: SET idempotency:{key} = txnId (24h)
    TXN-->>Customer: 202 Accepted {transactionId}

    POLLER->>OUTBOX: SELECT unpublished FOR UPDATE SKIP LOCKED
    POLLER->>KAFKA: Publish transfer.initiated

    KAFKA->>FRAUD: Consume transfer.initiated
    FRAUD->>Redis: Check blacklist
    FRAUD->>Redis: ZADD velocity:{accountId}:{hour}
    FRAUD->>KAFKA: Publish fraud.check.passed

    KAFKA->>ACC: Consume transfer.initiated (after fraud.check.passed)
    ACC->>ACC_DB: BEGIN TXN
    ACC->>ACC_DB: SELECT account FOR UPDATE (version=5)
    ACC->>ACC_DB: UPDATE balance, version=6
    ACC->>OUTBOX: INSERT outbox_event (account.debited)
    ACC->>ACC_DB: COMMIT
    ACC->>Redis: DEL balance:{fromAccountId}

    KAFKA->>ACC: account.debited → credit toAccount
    ACC->>ACC_DB: Credit toAccount
    ACC->>KAFKA: Publish account.credited

    KAFKA->>TXN: Consume account.credited
    TXN->>TXN_DB: UPDATE transaction status=COMPLETED
    TXN->>KAFKA: Publish transaction.completed

    KAFKA->>NOTIF: Consume transaction.completed
    NOTIF->>Customer: SMS + Email notification
```

---

## 4. Sequence Diagram — UPI Transfer

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant UPI as UPI Service
    participant Redis as Redis
    participant UPI_DB as UPI DB
    participant TXN as Transaction Service
    participant KAFKA as Kafka

    Customer->>GW: POST /upi/transfer {payerVpa, payeeVpa, amount, pin}
    GW->>UPI: Forward

    UPI->>UPI_DB: Find UpiId by payerVpa
    UPI->>UPI: BCrypt.matches(submittedPin, storedPinHash)
    alt PIN invalid
        UPI-->>Customer: 401 INVALID_PIN
    end

    UPI->>Redis: INCRBYFLOAT upi:limit:{upiId}:{today} += amount
    alt Daily limit exceeded
        UPI->>Redis: INCRBYFLOAT -= amount (rollback)
        UPI-->>Customer: 422 DAILY_LIMIT_EXCEEDED
    end

    UPI->>UPI_DB: BEGIN TXN
    UPI->>UPI_DB: INSERT upi_transaction (PENDING)
    UPI->>UPI_DB: COMMIT

    UPI->>TXN: POST /transfer (internal service call)
    TXN-->>UPI: 202 Accepted {transactionId}

    UPI-->>Customer: 202 Accepted {upiTransactionId}

    Note over KAFKA: Async completion
    KAFKA->>UPI: Consume transaction.completed
    UPI->>UPI_DB: UPDATE upi_transaction status=COMPLETED
    UPI->>KAFKA: Publish upi.transfer.completed
```

---

## 5. Sequence Diagram — Customer Registration

```mermaid
sequenceDiagram
    actor NewUser
    participant GW as API Gateway
    participant CUST as Customer Service
    participant AUTH as Auth Service
    participant CUST_DB as Customer DB
    participant Redis as Redis
    participant KAFKA as Kafka
    participant NOTIF as Notification Service

    NewUser->>GW: POST /customers/register {name, email, mobile, password}
    GW->>CUST: Forward (no JWT required)

    CUST->>CUST_DB: Check duplicate email/mobile
    alt Duplicate found
        CUST-->>NewUser: 409 DUPLICATE_EMAIL/MOBILE
    end

    CUST->>CUST_DB: BEGIN TXN
    CUST->>CUST_DB: INSERT customer (PENDING_VERIFICATION)
    CUST->>AUTH: Create user credentials (POST /internal/credentials)
    AUTH->>AUTH: BCrypt.hash(password)
    AUTH->>CUST_DB: INSERT user_credentials
    CUST->>CUST_DB: COMMIT
    CUST-->>NewUser: 201 Created {customerId, "OTP sent"}

    CUST->>AUTH: POST /otp/send {mobile, REGISTRATION}
    AUTH->>AUTH: Generate 6-digit OTP
    AUTH->>Redis: SET auth:otp:{mobile}:REGISTRATION {otp, generatedAt, attempts: 0} TTL=5m
    AUTH->>KAFKA: Publish notification.otp.requested

    KAFKA->>NOTIF: Consume
    NOTIF->>NewUser: SMS OTP to 9876543210

    NewUser->>GW: POST /auth/otp/verify {mobile, otp, REGISTRATION}
    GW->>AUTH: Forward
    AUTH->>Redis: GET auth:otp:{mobile}:REGISTRATION
    AUTH->>AUTH: Verify OTP match
    AUTH->>Redis: DEL auth:otp:{mobile}:REGISTRATION
    AUTH-->>GW: 200 Verified

    GW->>CUST: POST /customers/{id}/activate
    CUST->>CUST_DB: UPDATE customer status=PENDING_KYC
    CUST->>KAFKA: Publish customer.mobile.verified
    CUST-->>NewUser: 200 OK "Please submit KYC documents"
```

---

## 6. Activity Diagram — Fraud Detection

```mermaid
flowchart TD
    START([Receive transaction.initiated event]) --> A

    A[Extract account number and amount] --> B

    B{Is account in\nblacklist cache?}
    B -->|Yes| BLOCK1[severity=CRITICAL\nShouldBlock=true]
    B -->|No| C

    C[Get transaction count in last hour\nfrom Redis velocity set] --> D

    D{count > velocityThreshold?}
    D -->|Yes, count > 10| BLOCK2[severity=HIGH\nShouldBlock=true]
    D -->|No| E

    E{amount > largeTransactionThreshold\n₹1,00,000?}
    E -->|Yes| ALERT[severity=MEDIUM\nShouldBlock=false]
    E -->|No| PASS

    BLOCK1 --> SAVE_ALERT
    BLOCK2 --> SAVE_ALERT
    ALERT --> SAVE_ALERT
    SAVE_ALERT[Save FraudAlert to DB] --> CHECK_THRESHOLD

    CHECK_THRESHOLD{More than 3 HIGH/CRITICAL\nalerts for account in 24h?}
    CHECK_THRESHOLD -->|Yes| FREEZE[Publish fraud.account.frozen]
    CHECK_THRESHOLD -->|No| NOTIFY_ADMIN[Publish fraud.alert.raised]

    FREEZE --> END_BLOCK([Publish fraud.alert.raised\nwith shouldBlock=true])
    NOTIFY_ADMIN --> CONTINUE

    PASS[Publish fraud.check.passed] --> END_PASS([End])
    ALERT --> CONTINUE[Continue transaction\nwith alert in background]
    CONTINUE --> END_PASS
    END_BLOCK --> END([End])
```

---

## 7. Activity Diagram — Statement Generation

```mermaid
flowchart TD
    START([1st of month — Scheduler triggers]) --> A

    A[Query all active accounts from DB] --> B

    B[For each account\ncreate Statement record status=PENDING] --> C

    C[Publish statement.generation.requested\nto Kafka for each account] --> D

    D[Statement Service Consumer\nreceives event] --> E

    E[Query transactions for account\nfor the previous month\nfrom read replica] --> F

    F{Transactions found?}
    F -->|No| EMPTY[Generate empty statement PDF]
    F -->|Yes| CALC

    CALC[Calculate opening/closing balance\ntotal credits/debits\nrunning balance per transaction] --> G

    G[Generate PDF using iText/PDFBox] --> H

    H[Upload PDF to MinIO/S3\nkey: statements/{year}/{month}/{accountId}.pdf] --> I

    I[UPDATE statement\nstatus=GENERATED\ns3_key=...\ngenerated_at=now] --> J

    EMPTY --> I

    J[Publish statement.generated event] --> K

    K[Notification Service consumes\nSend email with download link] --> END([Done])

    G -->|Exception| FAIL[UPDATE status=FAILED\nPublish statement.generation.failed]
    FAIL --> RETRY{Retry count < 3?}
    RETRY -->|Yes| D
    RETRY -->|No| ALERT[Send alert to admin] --> END
```

---

## 8. State Diagram — Account Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING_ACTIVATION : Account created (KYC in progress)
    PENDING_ACTIVATION --> ACTIVE : KYC approved by admin
    PENDING_ACTIVATION --> CANCELLED : KYC rejected permanently

    ACTIVE --> FROZEN : freeze(reason)\nTriggered by admin or fraud detection
    FROZEN --> ACTIVE : unfreeze()\nAdmin action after review

    ACTIVE --> DORMANT : No transaction for 12 months\nScheduler triggers
    DORMANT --> ACTIVE : Any transaction\nCustomer reactivates

    ACTIVE --> CLOSURE_REQUESTED : closeAccount() by customer
    CLOSURE_REQUESTED --> CLOSED : Balance = 0 and no pending txns\nAdmin confirms
    CLOSURE_REQUESTED --> ACTIVE : Customer cancels or has pending balance

    FROZEN --> CLOSED : Admin force-close after fraud verdict

    CLOSED --> [*]
    CANCELLED --> [*]
```

---

## 9. State Diagram — Transaction Status

```mermaid
stateDiagram-v2
    [*] --> PENDING : Transaction record created

    PENDING --> FRAUD_CHECKING : fraud.check.started event consumed
    FRAUD_CHECKING --> DEBIT_PENDING : fraud.check.passed event consumed
    FRAUD_CHECKING --> FAILED : fraud.alert.raised (shouldBlock=true)

    DEBIT_PENDING --> CREDIT_PENDING : account.debited event consumed
    DEBIT_PENDING --> FAILED : debit.failed (insufficient funds)

    CREDIT_PENDING --> COMPLETED : account.credited event consumed
    CREDIT_PENDING --> COMPENSATING : credit.failed event consumed

    COMPENSATING --> REVERSED : debit.reversed (compensation complete)
    COMPENSATING --> MANUAL_REVIEW : reversal.failed (requires human intervention)

    COMPLETED --> REVERSAL_REQUESTED : Admin initiates reversal (T+1 only)
    REVERSAL_REQUESTED --> REVERSED : Reversal transaction completed

    FAILED --> [*]
    REVERSED --> [*]
    COMPLETED --> [*]
    MANUAL_REVIEW --> REVERSED : Admin manual resolution
    MANUAL_REVIEW --> COMPLETED : Admin confirms original was correct
```

---

## 10. State Diagram — Customer Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING_VERIFICATION : register()

    PENDING_VERIFICATION --> PENDING_KYC : verifyOtp()
    PENDING_VERIFICATION --> EXPIRED : OTP not verified in 24 hours

    PENDING_KYC --> ACTIVE : admin approveKyc()
    PENDING_KYC --> KYC_REJECTED : admin rejectKyc()

    KYC_REJECTED --> PENDING_KYC : customer resubmitKyc()

    ACTIVE --> FROZEN : freeze()\nAdmin or fraud detection
    FROZEN --> ACTIVE : unfreeze()

    ACTIVE --> DEACTIVATION_REQUESTED : customer requestAccountClosure()
    DEACTIVATION_REQUESTED --> CLOSED : All accounts closed + 30 day waiting period
    DEACTIVATION_REQUESTED --> ACTIVE : Customer cancels request

    FROZEN --> CLOSED : Admin force-close (fraud verdict)

    CLOSED --> [*]
    EXPIRED --> [*]
```

---

## 11. Component Diagram

```mermaid
graph TB
    subgraph Client Layer
        MB[Mobile App]
        WB[Web Browser]
        TP[Third-Party]
    end

    subgraph Edge Components
        LB{{Load Balancer}}
        GW[[API Gateway<br/>Spring Cloud Gateway]]
    end

    subgraph Core Service Components
        subgraph Auth
            AC[AuthController]
            AS[AuthService]
            TS[TokenService]
            OS[OtpService]
        end

        subgraph Transaction
            TC[TxnController]
            TSvc[TxnService]
            IS[IdempotencyService]
            SAGA[SagaCoordinator]
            OP[OutboxPoller]
        end

        subgraph Account
            ACCtrl[AccountController]
            AcSvc[AccountService]
            BC[BalanceCacheService]
            DL[DistributedLockService]
        end
    end

    subgraph Async Components
        NOTIF_C[NotificationConsumer]
        FRAUD_C[FraudConsumer]
        FR[FraudRuleChain]
        AUDIT_C[AuditConsumer]
    end

    subgraph Infrastructure
        KAFKA[(Kafka<br/>Broker)]
        PG[(PostgreSQL<br/>Primary)]
        PG_R[(PostgreSQL<br/>Replica)]
        REDIS[(Redis<br/>Cluster)]
    end

    MB & WB & TP --> LB
    LB --> GW
    GW --> Auth & Transaction & Account
    Transaction --> KAFKA
    Account --> KAFKA
    KAFKA --> NOTIF_C & FRAUD_C & AUDIT_C
    FRAUD_C --> FR
    Transaction --> PG
    Account --> PG & PG_R
    Auth & GW --> REDIS
    Transaction --> REDIS
```

---

## 12. Deployment Diagram

```mermaid
graph TB
    subgraph Internet
        USERS[Users<br/>Mobile + Web]
    end

    subgraph AWS Region ap-south-1
        subgraph AZ-1a
            ALB[Application Load Balancer]
            GW1[API Gateway Pod 1]
            TXN1[Transaction Service Pod 1]
            ACC1[Account Service Pod 1]
            PG_P[(RDS PostgreSQL<br/>Primary)]
        end

        subgraph AZ-1b
            GW2[API Gateway Pod 2]
            TXN2[Transaction Service Pod 2]
            ACC2[Account Service Pod 2]
            PG_R[(RDS PostgreSQL<br/>Replica)]
        end

        subgraph Managed Services
            MSK[Amazon MSK<br/>Kafka Cluster<br/>3 brokers]
            EC[ElastiCache Redis<br/>Cluster Mode]
            S3[Amazon S3<br/>Statements]
        end

        subgraph Monitoring
            CW[CloudWatch]
            PROM[Prometheus]
            GRAF[Grafana]
        end
    end

    USERS -->|HTTPS| ALB
    ALB --> GW1 & GW2
    GW1 & GW2 --> TXN1 & TXN2 & ACC1 & ACC2
    TXN1 & TXN2 & ACC1 & ACC2 --> MSK & EC & PG_P
    PG_P -->|Replication| PG_R
    TXN1 & TXN2 -.->|Reads| PG_R
    PROM --> GW1 & GW2 & TXN1 & TXN2
    GRAF --> PROM
```

---

> **Next:** [Folder Structure →](12-Folder-Structure.md)
