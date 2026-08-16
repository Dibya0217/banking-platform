# 08 — Redis Design

> **Navigation:** [← Kafka Design](07-Kafka-Design.md) | [Security →](09-Security.md)

---

## Table of Contents

1. [Redis Configuration](#1-redis-configuration)
2. [Use Case Inventory](#2-use-case-inventory)
3. [JWT Blacklist](#3-jwt-blacklist)
4. [OTP Storage](#4-otp-storage)
5. [Session and Refresh Token Storage](#5-session-and-refresh-token-storage)
6. [Idempotency Keys](#6-idempotency-keys)
7. [Rate Limiting](#7-rate-limiting)
8. [Account Balance Cache](#8-account-balance-cache)
9. [Customer Profile Cache](#9-customer-profile-cache)
10. [Distributed Locking](#10-distributed-locking)
11. [UPI Daily Limit Tracking](#11-upi-daily-limit-tracking)
12. [Fraud Blacklist Cache](#12-fraud-blacklist-cache)
13. [Cache Invalidation Strategy](#13-cache-invalidation-strategy)
14. [Redis Cluster Design](#14-redis-cluster-design)

---

## 1. Redis Configuration

### Spring Redis Configuration

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 1000ms
        cluster:
          refresh:
            adaptive: true
            period: 30s
```

### Redis Data Structure Usage

| Data Structure | Use Cases |
|---------------|-----------|
| `String` | OTP, idempotency keys, blacklisted JWT JTIs, simple flags |
| `Hash` | Customer profile cache, account balance with metadata |
| `Sorted Set` | Velocity check (transactions with timestamp score) |
| `String (INCR)` | Rate limiting counters, UPI daily limit tracking |
| `Set` | Blacklisted account numbers |

---

## 2. Use Case Inventory

| Key Pattern | Data Structure | TTL | Service | Purpose |
|------------|---------------|-----|---------|---------|
| `auth:blacklist:{jti}` | String | Token remaining validity | Auth / Gateway | Revoked access token |
| `auth:refresh:{userId}` | String | 7 days | Auth | Refresh token JTI |
| `auth:otp:{mobile}:{purpose}` | Hash | 5 minutes | Auth | One-time password |
| `auth:otp:attempts:{mobile}` | String | 15 minutes | Auth | Failed OTP attempt count |
| `idempotency:{key}` | String (JSON) | 24 hours | Transaction, UPI | Request deduplication |
| `ratelimit:{userId}:{windowStart}` | String | 1 minute | API Gateway | Per-user request count |
| `ratelimit:ip:{ip}:{windowStart}` | String | 1 minute | API Gateway | Per-IP request count |
| `balance:{accountId}` | Hash | 30 seconds | Account | Cached account balance |
| `customer:{customerId}` | Hash | 10 minutes | Customer | Cached customer profile |
| `upi:limit:{upiId}:{date}` | String | Until midnight | UPI | Daily UPI spend amount |
| `fraud:blacklist:accounts` | Set | 5 minutes | Fraud | Blacklisted account numbers |
| `lock:account:{accountId}` | String | 5 seconds | Account | Distributed lock for balance ops |
| `lock:upi-pin:{upiId}` | String | 10 seconds | UPI | PIN change lock |

---

## 3. JWT Blacklist

### Purpose
Store revoked JWT JTI (JWT ID) values so the API Gateway can reject them even though the JWT hasn't expired yet.

### Key Design
```
Key:   auth:blacklist:{jti}
Value: "1" (presence indicates revocation)
TTL:   remaining token validity (expiry - now)
```

### Implementation
```java
@Service
public class TokenBlacklistService {
    
    private final StringRedisTemplate redisTemplate;
    
    public void blacklist(String jti, long remainingValiditySeconds) {
        redisTemplate.opsForValue().set(
            "auth:blacklist:" + jti,
            "1",
            Duration.ofSeconds(remainingValiditySeconds)
        );
    }
    
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey("auth:blacklist:" + jti)
        );
    }
}
```

### Memory Estimate
- Max concurrent active users: 100,000
- JTI size: ~36 bytes; value "1": 1 byte; Redis overhead: ~70 bytes
- Total: 100,000 × 107 bytes ≈ **10 MB** — negligible

---

## 4. OTP Storage

### Key Design
```
Key:   auth:otp:{mobile}:{purpose}
Value: Hash { otp: "482910", generatedAt: "epoch", attempts: 0 }
TTL:   5 minutes

Key:   auth:otp:attempts:{mobile}
Value: attempt count (INCR)
TTL:   15 minutes (reset window)
```

### Purpose Values
- `REGISTRATION` — customer sign-up
- `FORGOT_PASSWORD` — password reset
- `TRANSACTION_AUTH` — high-value transaction confirmation
- `UPI_PIN_CHANGE` — UPI PIN modification

### Implementation
```java
@Service
public class OtpService {
    
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(15);
    
    public void storeOtp(String mobile, String purpose, String otp) {
        String key = "auth:otp:" + mobile + ":" + purpose;
        Map<String, String> data = Map.of(
            "otp", otp,
            "generatedAt", String.valueOf(Instant.now().getEpochSecond()),
            "attempts", "0"
        );
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, OTP_TTL);
    }
    
    public boolean verifyOtp(String mobile, String purpose, String submittedOtp) {
        String attemptsKey = "auth:otp:attempts:" + mobile;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, ATTEMPT_TTL);
        
        if (attempts > MAX_ATTEMPTS) {
            throw new OtpLockedException("Too many OTP attempts. Try again in 15 minutes.");
        }
        
        String key = "auth:otp:" + mobile + ":" + purpose;
        String storedOtp = (String) redisTemplate.opsForHash().get(key, "otp");
        
        if (storedOtp != null && storedOtp.equals(submittedOtp)) {
            redisTemplate.delete(key);              // Single-use: delete after success
            redisTemplate.delete(attemptsKey);      // Reset attempt counter
            return true;
        }
        return false;
    }
}
```

---

## 5. Session and Refresh Token Storage

### Key Design
```
Key:   auth:refresh:{userId}
Value: {jti} (the JTI of the current refresh token)
TTL:   7 days
```

### Usage
When a refresh token is presented:
1. Validate JWT signature
2. Extract `userId` and `jti` from token
3. Check Redis: `auth:refresh:{userId}` must equal the token's `jti`
4. This ensures only one refresh token is valid per user at a time (single-session policy)
5. On logout: `DEL auth:refresh:{userId}`

---

## 6. Idempotency Keys

### Key Design
```
Key:   idempotency:{idempotency-key-from-header}
Value: JSON { status: 201, body: {...} }
TTL:   24 hours
```

### Implementation
```java
@Aspect
@Component
public class IdempotencyAspect {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Around("@annotation(Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        String key = request.getHeader("Idempotency-Key");
        
        if (key == null) throw new MissingHeaderException("Idempotency-Key header required");
        
        String redisKey = "idempotency:" + key;
        String cached = redisTemplate.opsForValue().get(redisKey);
        
        if (cached != null) {
            return objectMapper.readValue(cached, ResponseEntity.class);  // Return cached
        }
        
        Object result = pjp.proceed();
        
        redisTemplate.opsForValue().set(
            redisKey,
            objectMapper.writeValueAsString(result),
            Duration.ofHours(24)
        );
        
        return result;
    }
}
```

---

## 7. Rate Limiting

### Algorithm: Sliding Window Counter

```
Key:   ratelimit:{userId}:{window-start-second}
Value: request count
TTL:   60 seconds

Limit: 100 requests per minute per user
```

### Implementation (Spring Cloud Gateway Filter)

```java
@Component
public class RateLimitGatewayFilter implements GatewayFilter {
    
    private static final int LIMIT = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        long windowStart = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:" + userId + ":" + windowStart;
        
        return redisTemplate.opsForValue()
            .increment(key)
            .flatMap(count -> {
                if (count == 1) redisTemplate.expire(key, WINDOW).subscribe();
                
                if (count > LIMIT) {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add(
                        "Retry-After", String.valueOf(60 - (Instant.now().getEpochSecond() % 60))
                    );
                    return exchange.getResponse().setComplete();
                }
                
                exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(LIMIT - count));
                return chain.filter(exchange);
            });
    }
}
```

### Rate Limit Tiers

| Endpoint Category | Limit |
|------------------|-------|
| `/auth/login` | 10 req/min per IP |
| `/auth/otp/send` | 5 req/hour per mobile |
| Standard APIs | 100 req/min per user |
| Admin APIs | 500 req/min per admin user |
| Statement download | 5 req/hour per user |

---

## 8. Account Balance Cache

### Key Design
```
Key:   balance:{accountId}
Value: Hash {
         balance: "50000.00",
         currency: "INR",
         status: "ACTIVE",
         cachedAt: "epoch-ms"
       }
TTL:   30 seconds
```

### Cache Invalidation
The balance cache is invalidated immediately on any debit/credit operation.

```java
@Service
public class BalanceCacheService {
    
    public Optional<BigDecimal> getCachedBalance(UUID accountId) {
        String key = "balance:" + accountId;
        Object cached = redisTemplate.opsForHash().get(key, "balance");
        return Optional.ofNullable(cached).map(v -> new BigDecimal((String) v));
    }
    
    public void cacheBalance(UUID accountId, BigDecimal balance, String currency, String status) {
        String key = "balance:" + accountId;
        Map<String, String> data = Map.of(
            "balance", balance.toPlainString(),
            "currency", currency,
            "status", status,
            "cachedAt", String.valueOf(System.currentTimeMillis())
        );
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, Duration.ofSeconds(30));
    }
    
    public void invalidateBalance(UUID accountId) {
        redisTemplate.delete("balance:" + accountId);
    }
}
```

### Cache-Aside Pattern (Account Service)

```java
public BalanceResponse getBalance(UUID accountId) {
    // 1. Try cache
    Optional<BigDecimal> cached = balanceCacheService.getCachedBalance(accountId);
    if (cached.isPresent()) {
        return BalanceResponse.fromCache(accountId, cached.get());
    }
    
    // 2. Cache miss → query database
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // 3. Populate cache
    balanceCacheService.cacheBalance(accountId, account.getBalance(), 
                                     account.getCurrency(), account.getStatus().name());
    
    return BalanceResponse.from(account);
}
```

---

## 9. Customer Profile Cache

```
Key:   customer:{customerId}
Value: Hash { id, fullName, email, maskedMobile, status, kycStatus, ... }
TTL:   10 minutes
```

Invalidated on any profile update or status change.

---

## 10. Distributed Locking

### Use Cases
- Balance update coordination (supplementary to optimistic locking)
- UPI PIN change (prevent concurrent PIN changes)
- Account freeze (prevent concurrent freeze/unfreeze)

### Implementation with Redisson

```java
@Service
public class DistributedLockService {
    
    private final RedissonClient redissonClient;
    
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, 
                                  Callable<T> action) throws Exception {
        RLock lock = redissonClient.getLock("lock:" + lockKey);
        
        boolean acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        if (!acquired) {
            throw new LockAcquisitionException("Could not acquire lock for: " + lockKey);
        }
        
        try {
            return action.call();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

// Usage in UPI PIN change:
lockService.executeWithLock(
    "upi-pin:" + upiId,
    waitSeconds = 5,
    leaseSeconds = 10,
    () -> {
        upiId.changePin(currentPin, newPin);
        upiRepository.save(upiId);
        return null;
    }
);
```

### Lock TTL Design
| Lock | Wait | Lease | Reason |
|------|------|-------|--------|
| `lock:account:{id}` | 3s | 5s | Balance operations are fast |
| `lock:upi-pin:{id}` | 5s | 10s | PIN change + DB write |
| `lock:account-freeze:{id}` | 2s | 8s | Status change + event publish |

---

## 11. UPI Daily Limit Tracking

```
Key:   upi:limit:{upiId}:{YYYY-MM-DD}
Value: Amount spent today (decimal string)
TTL:   Computed: seconds until midnight IST
```

```java
@Service
public class DailyLimitService {
    
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("100000");
    
    public void checkAndIncrementDailyLimit(UUID upiId, BigDecimal amount, BigDecimal limit) {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        String key = "upi:limit:" + upiId + ":" + today;
        
        // INCRBYFLOAT is atomic — no race condition
        Double newTotal = redisTemplate.opsForValue().increment(key, amount.doubleValue());
        
        // Set expiry to midnight IST (only on first increment)
        if (newTotal != null && newTotal.equals(amount.doubleValue())) {
            long secondsUntilMidnight = getSecondsUntilMidnightIST();
            redisTemplate.expire(key, Duration.ofSeconds(secondsUntilMidnight));
        }
        
        if (new BigDecimal(newTotal.toString()).compareTo(limit) > 0) {
            // Rollback the increment
            redisTemplate.opsForValue().increment(key, -amount.doubleValue());
            throw new DailyLimitExceededException(upiId, amount, limit);
        }
    }
}
```

---

## 12. Fraud Blacklist Cache

### Key Design
```
Key:   fraud:blacklist:accounts
Value: Set of account numbers
TTL:   5 minutes (refreshed by Fraud Service scheduler)
```

```java
@Scheduled(fixedDelay = 300_000)   // Refresh every 5 minutes
public void refreshBlacklistCache() {
    Set<String> blacklisted = blacklistedAccountRepository.findAllActiveAccountNumbers();
    String key = "fraud:blacklist:accounts";
    
    redisTemplate.delete(key);
    redisTemplate.opsForSet().add(key, blacklisted.toArray(new String[0]));
    redisTemplate.expire(key, Duration.ofMinutes(5));
}

public boolean isBlacklisted(String accountNumber) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForSet().isMember("fraud:blacklist:accounts", accountNumber)
    );
}
```

---

## 13. Cache Invalidation Strategy

| Event | Invalidated Keys | Trigger |
|-------|----------------|---------|
| Account balance changes | `balance:{accountId}` | After every debit/credit |
| Customer profile updated | `customer:{customerId}` | After profile PUT |
| Customer status changed | `customer:{customerId}` | On freeze/unfreeze |
| Token revoked | `auth:blacklist:{jti}` added | On logout |
| Blacklist updated | `fraud:blacklist:accounts` | Scheduled every 5 min |
| UPI PIN changed | `lock:upi-pin:{upiId}` released | After PIN change |

### Write-Through vs Cache-Aside

| Pattern | Used Where | Why |
|---------|-----------|-----|
| **Cache-Aside** | Balance, Customer Profile | DB is source of truth; cache is an optimization |
| **Write-Through** | Not used | Would require cache update in every transaction — adds latency |
| **TTL-based expiry** | All caches | Prevents stale data from living forever; balance TTL = 30s max |

---

## 14. Redis Cluster Design

```
6 nodes: 3 masters + 3 replicas
Master 1: Slots 0–5460    (keys: auth, otp)
Master 2: Slots 5461–10922 (keys: idempotency, ratelimit)
Master 3: Slots 10923–16383 (keys: balance, upi, fraud)

Each master has 1 replica for HA.
Automatic failover: ZooKeeper-free (Redis Sentinel or Redis Cluster mode)
```

### Redis Persistence
- **AOF (Append Only File)** enabled for auth keys (tokens, OTPs) — loss of these would require re-login
- **RDB snapshots** every hour for cache data (loss is acceptable — cache misses fall back to DB)

---

> **Next:** [Security →](09-Security.md)
