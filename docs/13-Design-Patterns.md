# 13 — Design Patterns

> **Navigation:** [← Folder Structure](12-Folder-Structure.md) | [Future Enhancements →](14-Future-Enhancements.md)

---

## Table of Contents

1. [Factory Pattern](#1-factory-pattern)
2. [Builder Pattern](#2-builder-pattern)
3. [Strategy Pattern](#3-strategy-pattern)
4. [Observer Pattern](#4-observer-pattern)
5. [Template Method Pattern](#5-template-method-pattern)
6. [Adapter Pattern](#6-adapter-pattern)
7. [Decorator Pattern](#7-decorator-pattern)
8. [Repository Pattern](#8-repository-pattern)
9. [Specification Pattern](#9-specification-pattern)
10. [Command Pattern](#10-command-pattern)
11. [Chain of Responsibility Pattern](#11-chain-of-responsibility-pattern)
12. [State Pattern](#12-state-pattern)
13. [Singleton Pattern](#13-singleton-pattern)
14. [Dependency Injection](#14-dependency-injection)
15. [Additional Patterns](#15-additional-patterns)

---

## 1. Factory Pattern

### Where: Notification Channel Selection

**Problem:** The `NotificationService` must send via Email, SMS, or Push depending on the customer's preferences. Hardcoding `if/else` chains violates Open/Closed Principle.

**Solution:** A `NotificationChannelFactory` returns the correct `NotificationChannel` implementation based on the channel type.

```java
public interface NotificationChannel {
    void send(Notification notification);
    NotificationChannelType getType();
}

@Component
public class EmailNotificationChannel implements NotificationChannel {
    public void send(Notification notification) { /* JavaMailSender logic */ }
    public NotificationChannelType getType() { return NotificationChannelType.EMAIL; }
}

@Component
public class SmsNotificationChannel implements NotificationChannel {
    public void send(Notification notification) { /* Twilio REST logic */ }
    public NotificationChannelType getType() { return NotificationChannelType.SMS; }
}

@Component
public class NotificationChannelFactory {
    
    private final Map<NotificationChannelType, NotificationChannel> channels;
    
    // Spring injects all NotificationChannel beans automatically
    public NotificationChannelFactory(List<NotificationChannel> channelList) {
        this.channels = channelList.stream()
            .collect(toMap(NotificationChannel::getType, identity()));
    }
    
    public NotificationChannel getChannel(NotificationChannelType type) {
        return Optional.ofNullable(channels.get(type))
            .orElseThrow(() -> new UnsupportedChannelException(type));
    }
}
```

**Why here:** Adding a new channel (e.g., WhatsApp) requires only adding a new `NotificationChannel` implementation — no changes to existing code.

---

### Where: Payment Processor Selection

**Problem:** Different payment types (UPI, NEFT, RTGS, IMPS) need different processing logic.

```java
public interface PaymentProcessor {
    TransactionResult process(PaymentRequest request);
    PaymentType getSupportedType();
}

@Component
public class PaymentProcessorFactory {
    private final Map<PaymentType, PaymentProcessor> processors;
    
    public PaymentProcessor getProcessor(PaymentType type) {
        return processors.get(type);
    }
}
```

---

## 2. Builder Pattern

### Where: Complex Request/Response Object Construction

**Problem:** `TransactionResponse`, `StatementResponse`, and `ApiResponse` have many optional fields. Using a constructor with 15 parameters is error-prone.

**Solution:** Lombok `@Builder` or manual builder for all DTO classes.

```java
@Builder
@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ErrorDetail error;
    private final String timestamp;
    private final String correlationId;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(Instant.now().toString())
            .correlationId(MDC.get("correlationId"))
            .build();
    }
    
    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder()
            .success(false)
            .error(ErrorDetail.builder().code(code).message(message).build())
            .timestamp(Instant.now().toString())
            .build();
    }
}
```

### Where: Test Data Construction (Builder in Tests)

```java
// In tests — readable, maintainable test data construction
Account account = Account.builder()
    .id(UUID.randomUUID())
    .customerId(customerId)
    .accountNumber("2026080012345678")
    .type(AccountType.SAVINGS)
    .balance(new BigDecimal("50000"))
    .status(AccountStatus.ACTIVE)
    .version(0L)
    .build();
```

---

## 3. Strategy Pattern

### Where: Fraud Detection Rules

**Problem:** Fraud rules (velocity, blacklist, large transaction) must be interchangeable and independently configurable. New rules must be addable without modifying existing code.

```java
@FunctionalInterface
public interface FraudRule {
    RuleResult evaluate(TransactionEvent transaction);
}

@Component
public class VelocityCheckRule implements FraudRule {
    public RuleResult evaluate(TransactionEvent txn) {
        long count = velocityTracker.countInLastHour(txn.getFromAccountId());
        return count > VELOCITY_THRESHOLD
            ? RuleResult.fraud("VELOCITY_CHECK", Severity.HIGH, true)
            : RuleResult.pass();
    }
}

@Component
public class BlacklistCheckRule implements FraudRule {
    public RuleResult evaluate(TransactionEvent txn) {
        return blacklistCache.isBlacklisted(txn.getFromAccountId())
            ? RuleResult.fraud("BLACKLIST", Severity.CRITICAL, true)
            : RuleResult.pass();
    }
}
```

The Strategy pattern allows rules to be enabled/disabled via configuration, making the fraud engine highly flexible.

---

### Where: Statement Export Format

**Problem:** Statements must be exported as PDF or CSV depending on user request.

```java
public interface StatementExporter {
    byte[] export(StatementData data);
    String getContentType();
    String getFileExtension();
}

@Component("pdf")
public class PdfStatementExporter implements StatementExporter { ... }

@Component("csv")
public class CsvStatementExporter implements StatementExporter { ... }

// Controller selects strategy based on Accept header
StatementExporter exporter = context.getBean(format, StatementExporter.class);
```

---

## 4. Observer Pattern

### Where: Domain Events via Kafka

**Problem:** When a transaction completes, multiple independent systems (Notification, Fraud, Audit, Statement) must react. Tight coupling to each subscriber violates Single Responsibility Principle.

**Solution:** Kafka implements the Observer pattern at distributed scale. The Transaction Service (Subject) publishes events; Notification, Fraud, Audit (Observers) subscribe independently.

```java
// Subject — Transaction Service
@Component
public class TransactionEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Notifies all observers by publishing to Kafka
    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        kafkaTemplate.send("banking.transaction.events", 
                           event.getFromAccountId(), event);
    }
}

// Observer 1 — Notification Service
@KafkaListener(topics = "banking.transaction.events", groupId = "notification-service-cg")
public void onTransactionCompleted(TransactionCompletedEvent event) {
    notificationService.sendTransactionAlert(event);
}

// Observer 2 — Audit Service (completely independent)
@KafkaListener(topics = "banking.transaction.events", groupId = "audit-service-cg")
public void onTransactionCompleted(TransactionCompletedEvent event) {
    auditService.record(event);
}
```

**Why here:** Kafka's consumer groups perfectly implement the Observer pattern — new observers can be added without changing the publisher.

---

### Where: Spring Application Events (within a service)

For in-process observers (e.g., Outbox event publication after a domain save):

```java
// Publisher
applicationEventPublisher.publishEvent(new AccountDebitedDomainEvent(accountId, amount));

// Observer (same JVM — for Outbox write)
@EventListener
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onAccountDebited(AccountDebitedDomainEvent event) {
    outboxRepository.save(new OutboxEvent(event));
}
```

---

## 5. Template Method Pattern

### Where: Base Notification Sender

**Problem:** Email, SMS, and Push notifications all follow the same flow: load preferences → format message → send → update status → retry on failure. Only the sending step differs.

```java
public abstract class BaseNotificationSender {
    
    // Template method — defines the algorithm skeleton
    public final void send(Notification notification) {
        if (!isPreferenceEnabled(notification)) return;
        
        String formattedMessage = formatMessage(notification);  // Common
        
        try {
            doSend(notification.getRecipient(), formattedMessage);  // Varies
            markSent(notification);   // Common
        } catch (Exception ex) {
            handleFailure(notification, ex);  // Common
        }
    }
    
    // Hook methods — subclasses implement these
    protected abstract void doSend(String recipient, String message);
    protected abstract boolean isPreferenceEnabled(Notification notification);
    
    // Common implementations
    private String formatMessage(Notification notification) { ... }
    private void markSent(Notification notification) { ... }
    private void handleFailure(Notification notification, Exception ex) { ... }
}

public class EmailSender extends BaseNotificationSender {
    protected void doSend(String recipient, String message) {
        javaMailSender.send(buildMimeMessage(recipient, message));
    }
}

public class SmsSender extends BaseNotificationSender {
    protected void doSend(String recipient, String message) {
        twilioClient.messages().create(recipient, message);
    }
}
```

---

## 6. Adapter Pattern

### Where: External Payment Provider Integration

**Problem:** Our system uses a standard `PaymentGateway` interface. Real banks use different API formats (NPCI, SWIFT). An Adapter converts between our internal format and their external API.

```java
// Our internal interface
public interface ExternalBankTransferGateway {
    TransferResult transfer(InternalTransferRequest request);
}

// NPCI's API expects a completely different format
public class NpciGatewayAdapter implements ExternalBankTransferGateway {
    
    private final NpciRestClient npciClient;
    
    public TransferResult transfer(InternalTransferRequest request) {
        // Adapt our format to NPCI's format
        NpciTransferRequest npciRequest = NpciTransferRequest.builder()
            .txnId(request.getTransactionId().toString())
            .payerIfsc(request.getFromIfsc())
            .payeeIfsc(request.getToIfsc())
            .amount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue()) // paise
            .build();
        
        NpciTransferResponse npciResponse = npciClient.initiateTransfer(npciRequest);
        
        // Adapt NPCI response back to our format
        return TransferResult.builder()
            .success(npciResponse.getStatus().equals("SUCCESS"))
            .referenceNumber(npciResponse.getRrn())
            .build();
    }
}
```

### Where: Mapping Between Layers

MapStruct `@Mapper` classes are adapters between JPA Entity and DTO layers:

```java
@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountResponse toResponse(Account account);
    Account toEntity(CreateAccountRequest request);
}
```

---

## 7. Decorator Pattern

### Where: Caching Decorator for Account Service

**Problem:** Add Redis caching to balance queries without modifying the core `AccountService` logic.

```java
// Core service
@Service
@Primary
public class CachedAccountService implements AccountService {
    
    private final AccountServiceImpl delegate;
    private final BalanceCacheService cache;
    
    public BalanceResponse getBalance(UUID accountId) {
        return cache.getCachedBalance(accountId)
            .map(balance -> BalanceResponse.fromCache(accountId, balance))
            .orElseGet(() -> {
                BalanceResponse response = delegate.getBalance(accountId);
                cache.cacheBalance(accountId, response.getBalance(), ...);
                return response;
            });
    }
}
```

### Where: Logging Decorator (AOP)

Spring AOP's `@Around` advice decorates service methods with logging:

```java
@Aspect
@Component
public class ServiceLoggingAspect {
    
    @Around("execution(* com.banking.*.service.*ServiceImpl.*(..))")
    public Object logServiceCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("Calling: {}", method);
        
        try {
            Object result = pjp.proceed();
            log.info("Completed: {} in {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception ex) {
            log.error("Failed: {} - {}", method, ex.getMessage());
            throw ex;
        }
    }
}
```

---

## 8. Repository Pattern

### Where: All Data Access

Spring Data JPA enforces the Repository pattern — data access is abstracted behind an interface; the business layer never writes raw SQL or manages connections.

```java
// Domain layer defines the contract
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status);
    
    @Query("SELECT a FROM Account a WHERE a.customerId = :customerId AND a.balance > 0")
    @Lock(LockModeType.OPTIMISTIC)
    List<Account> findActiveAccountsWithBalance(@Param("customerId") UUID customerId);
}
```

**Benefits:** Services are testable without a real database (mock the repository interface). Database implementation can change without touching service code.

---

## 9. Specification Pattern

### Where: Dynamic Transaction Filtering (Admin / History)

**Problem:** Transaction history has many optional filters: date range, amount range, type, status. Combining these dynamically with JPQL would require many `if` blocks.

```java
public class TransactionSpecifications {
    
    public static Specification<Transaction> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("fromAccountId"), accountId),
            cb.equal(root.get("toAccountId"), accountId)
        );
    }
    
    public static Specification<Transaction> afterDate(LocalDateTime from) {
        return (root, query, cb) -> from == null 
            ? cb.conjunction()
            : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }
    
    public static Specification<Transaction> byType(TransactionType type) {
        return (root, query, cb) -> type == null
            ? cb.conjunction()
            : cb.equal(root.get("type"), type);
    }
    
    public static Specification<Transaction> byStatus(TransactionStatus status) {
        return (root, query, cb) -> status == null
            ? cb.conjunction()
            : cb.equal(root.get("status"), status);
    }
}

// Usage — compose specifications dynamically
Specification<Transaction> spec = Specification.where(byAccountId(accountId))
    .and(afterDate(filter.getDateFrom()))
    .and(byType(filter.getType()))
    .and(byStatus(filter.getStatus()));

Page<Transaction> result = transactionRepository.findAll(spec, pageable);
```

---

## 10. Command Pattern

### Where: Transaction Reversal and Admin Actions

**Problem:** Reversal, freeze, and KYC approval are commands that must be logged (audited), retried, and potentially undone.

```java
public interface BankingCommand<T> {
    T execute();
    void undo();              // Compensating transaction
    String getCommandType();  // For audit logging
}

@Component
public class AccountFreezeCommand implements BankingCommand<Account> {
    
    private final Account account;
    private final String reason;
    private final AccountRepository repository;
    
    public Account execute() {
        account.freeze(reason);
        return repository.save(account);
    }
    
    public void undo() {
        account.unfreeze();
        repository.save(account);
    }
    
    public String getCommandType() { return "ACCOUNT_FREEZE"; }
}

@Service
public class CommandExecutor {
    
    public <T> T execute(BankingCommand<T> command) {
        auditLogger.logCommandExecution(command.getCommandType());
        T result = command.execute();
        auditLogger.logCommandSuccess(command.getCommandType());
        return result;
    }
}
```

---

## 11. Chain of Responsibility Pattern

### Where: Fraud Detection Rule Pipeline

**Problem:** Multiple fraud rules must be applied in sequence. If any rule flags the transaction, processing may stop (for blocking rules) or continue with alert.

```java
public abstract class FraudRuleHandler {
    
    protected FraudRuleHandler next;
    
    public FraudRuleHandler setNext(FraudRuleHandler next) {
        this.next = next;
        return next;
    }
    
    public abstract FraudResult handle(TransactionEvent event);
    
    protected FraudResult passToNext(TransactionEvent event) {
        return next != null ? next.handle(event) : FraudResult.passed();
    }
}

@Component
public class BlacklistRuleHandler extends FraudRuleHandler {
    public FraudResult handle(TransactionEvent event) {
        if (blacklistCache.isBlacklisted(event.getFromAccountId())) {
            return FraudResult.block("BLACKLIST", Severity.CRITICAL);  // Stop chain
        }
        return passToNext(event);  // Continue to next rule
    }
}

@Component
public class VelocityRuleHandler extends FraudRuleHandler {
    public FraudResult handle(TransactionEvent event) {
        if (velocityTracker.exceedsLimit(event.getFromAccountId())) {
            return FraudResult.block("VELOCITY", Severity.HIGH);
        }
        return passToNext(event);
    }
}

@Component
public class LargeTransactionRuleHandler extends FraudRuleHandler {
    public FraudResult handle(TransactionEvent event) {
        if (event.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            return FraudResult.alert("LARGE_TXN", Severity.MEDIUM);  // Continue but alert
        }
        return passToNext(event);
    }
}

// Chain assembly (in Configuration class)
@Bean
public FraudRuleHandler fraudRuleChain() {
    BlacklistRuleHandler blacklist = new BlacklistRuleHandler(blacklistCache);
    VelocityRuleHandler velocity = new VelocityRuleHandler(velocityTracker);
    LargeTransactionRuleHandler largeTransaction = new LargeTransactionRuleHandler();
    
    blacklist.setNext(velocity).setNext(largeTransaction);
    return blacklist;
}
```

### Where: Request Filter Chain (Spring Security)

Spring Security's filter chain is a canonical Chain of Responsibility:
1. `CorrelationIdFilter` → 2. `JwtExtractionFilter` → 3. `RateLimitFilter` → 4. `AuthorizationFilter` → Controller

---

## 12. State Pattern

### Where: Account Status Transitions

**Problem:** Account behavior changes based on its status. A `FROZEN` account should reject debits. A `CLOSED` account should reject all operations.

```java
public enum AccountStatus {
    ACTIVE {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            if (account.getBalance().compareTo(amount) < 0)
                throw new InsufficientFundsException(account.getId(), amount);
        }
        @Override
        public void validateCredit(Account account) { /* Always allowed */ }
    },
    FROZEN {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            throw new AccountFrozenException(account.getId());
        }
        @Override
        public void validateCredit(Account account) {
            throw new AccountFrozenException(account.getId());
        }
    },
    CLOSED {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            throw new AccountClosedException(account.getId());
        }
        @Override
        public void validateCredit(Account account) {
            throw new AccountClosedException(account.getId());
        }
    };
    
    public abstract void validateDebit(Account account, BigDecimal amount);
    public abstract void validateCredit(Account account);
}

// Usage in Account entity
public void debit(BigDecimal amount) {
    status.validateDebit(this, amount);  // State-aware validation
    this.balance = this.balance.subtract(amount);
}
```

---

## 13. Singleton Pattern

### Where: Infrastructure Beans (Spring-managed)

Spring IOC container manages beans as singletons by default — the correct way to implement Singleton in a Spring application.

```java
@Component   // Singleton scope by default
public class TokenService {
    // One instance shared across the application context
    // Thread-safe if stateless (no mutable instance fields)
}

@Bean        // Also singleton by default
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");
    return Redisson.create(config);  // Single Redisson client per application
}
```

**Why not static Singleton:** Spring's DI-managed singletons are testable (can be mocked), configurable (properties injected), and respect the application lifecycle (proper `@PreDestroy` shutdown).

---

## 14. Dependency Injection

### Where: Everywhere (Core Principle)

Spring's DI is used throughout the platform. Constructor injection is preferred over field injection for testability and immutability.

```java
@Service
public class TransactionServiceImpl implements TransactionService {
    
    // Constructor injection — all dependencies are explicit and testable
    private final TransactionRepository repository;
    private final IdempotencyService idempotencyService;
    private final TransactionEventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    
    // @Autowired is implicit on single constructor (Spring 4.3+)
    public TransactionServiceImpl(
            TransactionRepository repository,
            IdempotencyService idempotencyService,
            TransactionEventPublisher eventPublisher,
            OutboxRepository outboxRepository) {
        this.repository = repository;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
    }
}
```

**Why constructor injection over `@Autowired` on fields:**
- Immutability — fields can be `final`
- Explicit dependencies — visible in the constructor signature
- Testability — no Spring context needed in unit tests; pass mocks directly

---

## 15. Additional Patterns

### Outbox Pattern
See [Kafka Design — Outbox Pattern](07-Kafka-Design.md#6-outbox-pattern-integration)

Ensures at-least-once Kafka event delivery by writing events to a database table in the same transaction as the domain change, then publishing via a background poller.

### Saga Pattern (Choreography)
See [Architecture Decisions — Saga Pattern](02-Architecture-Decisions.md#adr-013-saga-pattern-choreography)

Manages distributed transactions across services without 2PC. Each service reacts to events and publishes its own events; compensation events handle rollback.

### CQRS (Command Query Responsibility Segregation)
Applied to Transaction Service and Statement Service. Write commands go to the primary database; read queries use the read replica or a separate read model.

### Circuit Breaker
Resilience4j wraps external service calls. After N failures, the circuit opens and requests fail-fast with a fallback for 30 seconds before retrying.

```java
@CircuitBreaker(name = "account-service", fallbackMethod = "accountServiceFallback")
public AccountResponse getAccount(UUID accountId) {
    return accountServiceClient.getAccount(accountId);
}

public AccountResponse accountServiceFallback(UUID accountId, Exception ex) {
    throw new ServiceUnavailableException("Account Service temporarily unavailable");
}
```

### Idempotency Key Pattern
All mutation APIs accept a client-provided `Idempotency-Key` header. The server stores the response in Redis for 24 hours. Repeat requests with the same key return the stored response without re-execution.

---

> **Next:** [Future Enhancements →](14-Future-Enhancements.md)
