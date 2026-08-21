# Banking System — Complete Testing & API Guide

> **Read this before touching Postman.**  
> This guide covers: project setup → starting services → every API endpoint with real request/response examples → full end-to-end flows.

---

## Table of Contents

1. [Project Status — What Is Complete](#1-project-status)
2. [Infrastructure Setup](#2-infrastructure-setup)
3. [Starting All Services](#3-starting-all-services)
4. [Service Port Map](#4-service-port-map)
5. [Postman Setup](#5-postman-setup)
6. [API Reference — Every Endpoint](#6-api-reference)
   - [Auth Service](#61-auth-service-port-8081)
   - [Customer Service](#62-customer-service-port-8082)
   - [Account Service](#63-account-service-port-8083)
   - [Transaction Service](#64-transaction-service-port-8084)
   - [Beneficiary Service](#65-beneficiary-service-port-8085)
   - [UPI Service](#66-upi-service-port-8086)
   - [Fraud Detection Service](#67-fraud-detection-service-port-8090)
   - [Statement Service](#68-statement-service-port-8088)
   - [Admin Service](#69-admin-service-port-8089)
   - [Audit Service](#610-audit-service-port-8091)
7. [End-to-End Test Flows](#7-end-to-end-test-flows)
   - [Flow 1: Full Customer Onboarding](#flow-1-full-customer-onboarding)
   - [Flow 2: Deposit & Withdraw](#flow-2-deposit--withdraw)
   - [Flow 3: Fund Transfer (Saga)](#flow-3-fund-transfer-saga)
   - [Flow 4: Beneficiary + Transfer](#flow-4-beneficiary--transfer)
   - [Flow 5: UPI Transfer](#flow-5-upi-transfer)
   - [Flow 6: Fraud Detection](#flow-6-fraud-detection)
   - [Flow 7: Statement Download](#flow-7-statement-download)
   - [Flow 8: Admin Operations](#flow-8-admin-operations)
8. [Verifying Background Systems](#8-verifying-background-systems)
9. [Known Limitations (Dev Mode)](#9-known-limitations-dev-mode)

---

## 1. Project Status

| Service | Port | Status |
|---------|------|--------|
| api-gateway | 8080 | ✅ JWT auth, rate limiting, routing |
| auth-service | 8081 | ✅ Login, OTP, JWT, token blacklist |
| customer-service | 8082 | ✅ Registration, KYC, profile |
| account-service | 8083 | ✅ Accounts, balance cache, optimistic lock |
| transaction-service | 8084 | ✅ Deposit, withdraw, transfer (Choreography Saga) |
| beneficiary-service | 8085 | ✅ Add/verify, penny-drop, cooldown |
| upi-service | 8086 | ✅ VPA, PIN, daily limit |
| notification-service | 8087 | ✅ Email (MailHog), SMS/Push stubbed |
| statement-service | 8088 | ✅ PDF generation, MinIO storage |
| admin-service | 8089 | ✅ Admin proxy for all operations |
| fraud-detection-service | 8090 | ✅ Velocity, blacklist, large-txn rules |
| audit-service | 8091 | ✅ Append-only audit log from all Kafka topics |
| eureka-service | 8761 | ✅ Service registry |
| config-service | 8888 | ✅ Centralized config |

---

## 2. Infrastructure Setup

### Step 1: Configure environment

```bash
cd /path/to/banking-system
cp .env.example .env
# Edit .env — change passwords and JWT_SECRET at minimum
```

### Step 2: Start all infrastructure containers

```bash
cd infrastructure/docker
docker-compose up -d
```

### Step 3: Verify all containers are healthy

```bash
docker-compose ps
# All should show: Up (healthy)
```

Expected containers:
| Container | Port | Purpose |
|-----------|------|---------|
| banking-postgres | 5434 | PostgreSQL database |
| banking-redis | 6380 | Token store, cache, rate limits |
| banking-kafka | 9092 | Event streaming |
| banking-zookeeper | — | Kafka dependency |
| banking-kafdrop | 9100 | Kafka UI — see topics and messages |
| banking-minio | 9094 | PDF statement storage |
| banking-mailhog | 8025 | Email catcher (check emails here) |
| banking-prometheus | 9093 | Metrics collection |
| banking-grafana | 3000 | Dashboards (admin/admin123) |
| banking-elasticsearch | 9200 | Log aggregation |
| banking-kibana | 5601 | Log search UI |

### Step 4: Quick infrastructure health check

```bash
# PostgreSQL
docker exec banking-postgres psql -U banking -c "\dn" | grep -E "auth|customer|account|transaction"

# Redis
redis-cli -h localhost -p 6380 -a banking123 PING
# → PONG

# Kafka topics
docker exec banking-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# MinIO
curl -s http://localhost:9094/minio/health/live && echo " → OK"

# MailHog UI
open http://localhost:8025
```

---

## 3. Starting All Services

### Build shared libraries first (required once)

```bash
# From project root
./mvnw install -pl shared/banking-commons,shared/banking-events -am -DskipTests
```

### Start services (order matters for dependencies)

```bash
# Terminal 1 — Registry first
./mvnw spring-boot:run -pl services/eureka-service

# Terminal 2 — Config
./mvnw spring-boot:run -pl services/config-service

# Terminal 3 — Auth (no dependencies on other services)
./mvnw spring-boot:run -pl services/auth-service

# Terminal 4 — Customer
./mvnw spring-boot:run -pl services/customer-service

# Terminal 5 — Account
./mvnw spring-boot:run -pl services/account-service

# Terminal 6 — Transaction
./mvnw spring-boot:run -pl services/transaction-service

# Terminal 7 — Beneficiary
./mvnw spring-boot:run -pl services/beneficiary-service

# Terminal 8 — UPI
./mvnw spring-boot:run -pl services/upi-service

# Terminal 9 — Fraud Detection
./mvnw spring-boot:run -pl services/fraud-detection-service

# Terminal 10 — Notification
./mvnw spring-boot:run -pl services/notification-service

# Terminal 11 — Statement
./mvnw spring-boot:run -pl services/statement-service

# Terminal 12 — Audit
./mvnw spring-boot:run -pl services/audit-service

# Terminal 13 — Admin
./mvnw spring-boot:run -pl services/admin-service

# Terminal 14 — Gateway LAST (routes to all others)
./mvnw spring-boot:run -pl services/api-gateway
```

### Verify all services are up

```bash
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091; do
  status=$(curl -s http://localhost:$port/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','ERR'))" 2>/dev/null || echo "DOWN")
  echo "Port $port: $status"
done
```

---

## 4. Service Port Map

```
Gateway  → 8080  (everything goes through here in production)
Auth     → 8081
Customer → 8082
Account  → 8083
Transaction → 8084
Beneficiary → 8085
UPI      → 8086
Notification → 8087
Statement → 8088
Admin    → 8089
Fraud    → 8090
Audit    → 8091
Eureka   → 8761
Config   → 8888
```

> **Important for Postman:** All requests should go through port **8080** (gateway). The gateway handles JWT validation. Direct service calls on individual ports skip auth — useful only for debugging.

---

## 5. Postman Setup

### Create a Postman Collection

**Collection name:** `Banking System`

**Collection variables:**
| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `BASE_URL` | `http://localhost:8080` | Gateway URL |
| `ACCESS_TOKEN` | *(empty — set after login)* | JWT access token |
| `REFRESH_TOKEN` | *(empty — set after login)* | JWT refresh token |
| `CUSTOMER_ID` | *(empty — set after register)* | Current customer UUID |
| `ACCOUNT_ID` | *(empty — set after account creation)* | Primary account UUID |

### Add auto-token extraction script

In the **Login** request → **Tests** tab, add:
```javascript
const json = pm.response.json();
if (json.data) {
    pm.collectionVariables.set("ACCESS_TOKEN", json.data.accessToken);
    pm.collectionVariables.set("REFRESH_TOKEN", json.data.refreshToken);
}
```

### Authorization header for all authenticated requests

In each request → **Auth** tab:
- Type: `Bearer Token`
- Token: `{{ACCESS_TOKEN}}`

Or set at Collection level so all requests inherit it.

---

## 6. API Reference

> All responses follow the wrapper: `{ "success": true, "data": {...}, "error": null, "timestamp": "...", "correlationId": "..." }`

---

### 6.1 Auth Service (Port 8081)

#### POST /api/v1/auth/otp/send

Send OTP to a mobile number. In dev, OTP is logged to console (not actually sent via SMS).

```
POST http://localhost:8080/api/v1/auth/otp/send
Content-Type: application/json

{
  "mobile": "9876543210",
  "purpose": "REGISTRATION"
}
```

**purpose values:** `REGISTRATION`, `LOGIN`, `FORGOT_PASSWORD`, `TRANSACTION`

**Response:** `200 OK`
```json
{ "success": true, "data": null }
```

> **Dev tip:** Check the auth-service console log for the OTP. It will print: `Generated OTP for 9876543210: 482931`

---

#### POST /api/v1/auth/otp/verify

```
POST http://localhost:8080/api/v1/auth/otp/verify
Content-Type: application/json

{
  "mobile": "9876543210",
  "otp": "482931",
  "purpose": "REGISTRATION"
}
```

**Response:** `200 OK`
```json
{ "success": true, "data": null }
```

---

#### POST /api/v1/auth/login

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "priya@example.com",
  "password": "Test@1234"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

> Save `accessToken` to `{{ACCESS_TOKEN}}` and `refreshToken` to `{{REFRESH_TOKEN}}`.

**Error cases:**
- `401` — wrong password
- `423` — account locked (5 failed attempts)

---

#### POST /api/v1/auth/refresh

```
POST http://localhost:8080/api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{{REFRESH_TOKEN}}"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

---

#### POST /api/v1/auth/logout

```
POST http://localhost:8080/api/v1/auth/logout
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`
```json
{ "success": true, "data": null }
```

After logout, the old `ACCESS_TOKEN` is blacklisted in Redis — any request with it returns `401`.

---

### 6.2 Customer Service (Port 8082)

#### POST /api/v1/customers/register

Public endpoint — no auth required.

```
POST http://localhost:8080/api/v1/customers/register
Content-Type: application/json

{
  "fullName": "Priya Sharma",
  "email": "priya@example.com",
  "mobile": "9876543210",
  "password": "Test@1234",
  "dateOfBirth": "1990-05-15"
}
```

**Validation rules:**
- `fullName`: 2–100 characters
- `email`: valid email format
- `mobile`: Indian mobile — must start with 6–9, exactly 10 digits
- `password`: min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char
- `dateOfBirth`: must be 18+ years old, format `YYYY-MM-DD`

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING_VERIFICATION",
    "message": "Registration successful. Verify your mobile to continue."
  }
}
```

> Save `customerId` to `{{CUSTOMER_ID}}`.

**Error cases:**
- `409` — email or mobile already registered
- `400` — age < 18

---

#### POST /api/v1/customers/{id}/verify-mobile

Call this after OTP verify to advance the customer status to `PENDING_KYC`.

```
POST http://localhost:8080/api/v1/customers/{{CUSTOMER_ID}}/verify-mobile
```

**Response:** `200 OK`

---

#### POST /api/v1/customers/{id}/kyc

Submit KYC documents.

```
POST http://localhost:8080/api/v1/customers/{{CUSTOMER_ID}}/kyc
Authorization: Bearer {{ACCESS_TOKEN}}
Content-Type: application/json

{
  "documentType": "AADHAAR",
  "documentNumber": "123456789012",
  "documentUrl": "https://example.com/aadhaar.pdf"
}
```

**documentType values:** `AADHAAR`, `PAN`, `PASSPORT`, `DRIVING_LICENSE`, `VOTER_ID`

**Response:** `201 Created`
```json
{
  "success": true,
  "data": null
}
```

---

#### GET /api/v1/customers/{id}

```
GET http://localhost:8080/api/v1/customers/{{CUSTOMER_ID}}
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "Priya Sharma",
    "email": "priya@example.com",
    "mobile": "9876XXXXXX",
    "status": "PENDING_KYC",
    "createdAt": "2026-08-22T10:30:00Z"
  }
}
```

---

#### GET /api/v1/customers (Admin only)

```
GET http://localhost:8080/api/v1/customers?page=0&size=20&search=priya
Authorization: Bearer {{ADMIN_TOKEN}}
```

**Response:** `200 OK` — paginated list of customers.

---

### 6.3 Account Service (Port 8083)

#### POST /api/v1/accounts

Create account (customer must be ACTIVE — i.e., KYC approved first).

```
POST http://localhost:8080/api/v1/accounts
Authorization: Bearer {{ACCESS_TOKEN}}
Content-Type: application/json

{
  "customerId": "{{CUSTOMER_ID}}",
  "accountType": "SAVINGS"
}
```

**accountType values:** `SAVINGS`, `CURRENT`, `FIXED_DEPOSIT`

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-...",
    "accountNumber": "2026082200012345",
    "accountType": "SAVINGS",
    "balance": 0.00,
    "currency": "INR",
    "status": "ACTIVE",
    "customerId": "{{CUSTOMER_ID}}",
    "createdAt": "2026-08-22T10:35:00Z"
  }
}
```

> Save `id` to `{{ACCOUNT_ID}}`.

> **Note:** An account is auto-created when KYC is approved (via Kafka event). You may already have one — check `GET /api/v1/accounts?customerId=...` first.

---

#### GET /api/v1/accounts?customerId={id}

```
GET http://localhost:8080/api/v1/accounts?customerId={{CUSTOMER_ID}}
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK` — array of all accounts for the customer.

---

#### GET /api/v1/accounts/{id}/balance

```
GET http://localhost:8080/api/v1/accounts/{{ACCOUNT_ID}}/balance
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "accountId": "a1b2c3d4-...",
    "balance": 10000.00,
    "currency": "INR",
    "status": "ACTIVE",
    "cached": true
  }
}
```

> Second call returns `"cached": true` — Redis cache-aside in action.

---

#### POST /api/v1/accounts/{id}/freeze (Admin only)

```
POST http://localhost:8080/api/v1/accounts/{{ACCOUNT_ID}}/freeze
Authorization: Bearer {{ADMIN_TOKEN}}
Content-Type: application/json

{
  "reason": "Suspicious transaction pattern"
}
```

**Response:** `200 OK`

---

#### DELETE /api/v1/accounts/{id}

Close account (balance must be zero).

```
DELETE http://localhost:8080/api/v1/accounts/{{ACCOUNT_ID}}
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`

---

### 6.4 Transaction Service (Port 8084)

> **Important:** All write endpoints require an `Idempotency-Key` header (UUID). Same key = same response returned from cache. Generate a new UUID per request.

#### POST /api/v1/transactions/deposit

Synchronous — balance is updated immediately.

```
POST http://localhost:8080/api/v1/transactions/deposit
Authorization: Bearer {{ACCESS_TOKEN}}
Idempotency-Key: {{$guid}}
Content-Type: application/json

{
  "accountId": "{{ACCOUNT_ID}}",
  "amount": 50000,
  "description": "Initial deposit",
  "reference": "REF001"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "txn-uuid",
    "type": "DEPOSIT",
    "status": "COMPLETED",
    "amount": 50000.00,
    "currency": "INR",
    "accountId": "{{ACCOUNT_ID}}",
    "description": "Initial deposit",
    "createdAt": "2026-08-22T10:40:00Z"
  }
}
```

---

#### POST /api/v1/transactions/withdraw

Synchronous — balance is updated immediately.

```
POST http://localhost:8080/api/v1/transactions/withdraw
Authorization: Bearer {{ACCESS_TOKEN}}
Idempotency-Key: {{$guid}}
Content-Type: application/json

{
  "accountId": "{{ACCOUNT_ID}}",
  "amount": 5000,
  "description": "ATM withdrawal"
}
```

**Response:** `201 Created` — same structure as deposit, type=`WITHDRAW`.

**Error cases:**
- `400` — insufficient balance
- `400` — account frozen or closed

---

#### POST /api/v1/transactions/transfer

**Asynchronous (Choreography Saga).** Returns `202 ACCEPTED` immediately. The transfer completes in the background via Kafka events.

```
POST http://localhost:8080/api/v1/transactions/transfer
Authorization: Bearer {{ACCESS_TOKEN}}
Idempotency-Key: {{$guid}}
Content-Type: application/json

{
  "fromAccountId": "{{ACCOUNT_ID}}",
  "toAccountId": "{{TO_ACCOUNT_ID}}",
  "amount": 5000,
  "description": "Payment to Rahul"
}
```

**Response:** `202 Accepted`
```json
{
  "success": true,
  "data": {
    "id": "txn-uuid",
    "type": "TRANSFER",
    "status": "FRAUD_CHECKING",
    "amount": 5000.00,
    "fromAccountId": "{{ACCOUNT_ID}}",
    "toAccountId": "{{TO_ACCOUNT_ID}}"
  }
}
```

**Saga states (poll GET /transactions/{id} to track):**
```
FRAUD_CHECKING → DEBIT_PENDING → CREDIT_PENDING → COMPLETED
                                                 ↘ FAILED (compensation reverses debit)
```

---

#### GET /api/v1/transactions/{id}

```
GET http://localhost:8080/api/v1/transactions/{{TRANSACTION_ID}}
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK` — transaction with current status.

---

#### GET /api/v1/transactions?accountId={id}

Paginated history with optional filters.

```
GET http://localhost:8080/api/v1/transactions?accountId={{ACCOUNT_ID}}&page=0&size=20
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Optional filter params:**
- `type` — `DEPOSIT`, `WITHDRAW`, `TRANSFER`
- `status` — `COMPLETED`, `FAILED`, `PENDING`
- `dateFrom` — `2026-08-01`
- `dateTo` — `2026-08-31`
- `minAmount` — `1000`
- `maxAmount` — `50000`

---

#### POST /api/v1/transactions/{id}/reverse (Admin only)

```
POST http://localhost:8080/api/v1/transactions/{{TRANSACTION_ID}}/reverse
Authorization: Bearer {{ADMIN_TOKEN}}
```

**Response:** `200 OK` — creates a reversal transaction.

---

### 6.5 Beneficiary Service (Port 8085)

#### POST /api/v1/beneficiaries

Add a beneficiary. Triggers **async penny-drop** (2 second simulation in dev).

```
POST http://localhost:8080/api/v1/beneficiaries
Authorization: Bearer {{ACCESS_TOKEN}}
Content-Type: application/json

{
  "accountNumber": "1234567890123456",
  "ifscCode": "HDFC0001234",
  "beneficiaryName": "Rahul Kumar",
  "bankName": "HDFC Bank",
  "nickname": "Rahul"
}
```

**IFSC validation:** Must match `[A-Z]{4}0[A-Z0-9]{6}`

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "ben-uuid",
    "accountNumber": "1234567890123456",
    "ifscCode": "HDFC0001234",
    "beneficiaryName": "Rahul Kumar",
    "status": "PENDING_VERIFICATION",
    "transferEnabledAt": null
  }
}
```

After ~2 seconds, status becomes `ACTIVE` and `transferEnabledAt` is set to `now + 24 hours` (cooldown). In dev, you can reduce the cooldown by setting `beneficiary.cooldown-hours=0` in config.

---

#### GET /api/v1/beneficiaries

```
GET http://localhost:8080/api/v1/beneficiaries
Authorization: Bearer {{ACCESS_TOKEN}}
```

---

#### GET /api/v1/beneficiaries/{id}/verify

Check if transfer is allowed (active + cooldown passed).

```
GET http://localhost:8080/api/v1/beneficiaries/{{BENEFICIARY_ID}}/verify
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": { "transferAllowed": true }
}
```

---

#### DELETE /api/v1/beneficiaries/{id}

```
DELETE http://localhost:8080/api/v1/beneficiaries/{{BENEFICIARY_ID}}
Authorization: Bearer {{ACCESS_TOKEN}}
```

---

### 6.6 UPI Service (Port 8086)

#### POST /api/v1/upi

Register a VPA (Virtual Payment Address).

```
POST http://localhost:8080/api/v1/upi
Authorization: Bearer {{ACCESS_TOKEN}}
Content-Type: application/json

{
  "accountId": "{{ACCOUNT_ID}}",
  "vpa": "priya@bank",
  "pin": "123456"
}
```

**VPA format:** `username@bankcode` — alphanumeric, 3–50 chars.

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "upi-uuid",
    "vpa": "priya@bank",
    "accountId": "{{ACCOUNT_ID}}",
    "status": "ACTIVE",
    "dailyLimitUsed": 0.00,
    "dailyLimit": 100000.00
  }
}
```

---

#### GET /api/v1/upi

```
GET http://localhost:8080/api/v1/upi
Authorization: Bearer {{ACCESS_TOKEN}}
```

---

#### PUT /api/v1/upi/{id}/pin

Change UPI PIN (protected by distributed Redis lock).

```
PUT http://localhost:8080/api/v1/upi/{{UPI_ID}}/pin
Authorization: Bearer {{ACCESS_TOKEN}}
Content-Type: application/json

{
  "currentPin": "123456",
  "newPin": "654321"
}
```

---

#### POST /api/v1/upi/transfer

PIN verified, daily limit enforced (₹1,00,000/day via Redis).

```
POST http://localhost:8080/api/v1/upi/transfer
Authorization: Bearer {{ACCESS_TOKEN}}
Idempotency-Key: {{$guid}}
Content-Type: application/json

{
  "payerVpa": "priya@bank",
  "payeeVpa": "rahul@hdfc",
  "amount": 500,
  "pin": "123456",
  "description": "Lunch payment"
}
```

**Response:** `202 Accepted`
```json
{
  "success": true,
  "data": {
    "id": "upi-txn-uuid",
    "payerVpa": "priya@bank",
    "payeeVpa": "rahul@hdfc",
    "amount": 500.00,
    "status": "INITIATED"
  }
}
```

**Error cases:**
- `400` — wrong PIN
- `400` — daily limit exceeded
- `400` — payee VPA not found

---

#### GET /api/v1/upi/{id}/transactions

```
GET http://localhost:8080/api/v1/upi/{{UPI_ID}}/transactions?page=0&size=20
Authorization: Bearer {{ACCESS_TOKEN}}
```

---

### 6.7 Fraud Detection Service (Port 8090)

> Fraud checks run automatically on every transfer via Kafka. These endpoints are for Admin to manage alerts.

#### GET /api/v1/fraud/alerts (Admin only)

```
GET http://localhost:8080/api/v1/fraud/alerts?status=OPEN&page=0&size=20
Authorization: Bearer {{ADMIN_TOKEN}}
```

**status values:** `OPEN`, `RESOLVED`, `FALSE_POSITIVE`

**Response:** `200 OK` — paginated fraud alerts.

---

#### PATCH /api/v1/fraud/alerts/{alertId}/resolve (Admin only)

```
PATCH http://localhost:8080/api/v1/fraud/alerts/{{ALERT_ID}}/resolve
Authorization: Bearer {{ADMIN_TOKEN}}
Content-Type: application/json

{
  "note": "Investigated — legitimate transaction by customer"
}
```

---

#### POST /api/v1/fraud/blacklist (Admin only)

Immediately blacklist an account — all future transactions blocked instantly.

```
POST http://localhost:8080/api/v1/fraud/blacklist
Authorization: Bearer {{ADMIN_TOKEN}}
Content-Type: application/json

{
  "accountId": "{{ACCOUNT_ID}}",
  "reason": "Reported fraud account"
}
```

---

### 6.8 Statement Service (Port 8088)

#### GET /api/v1/statements/{accountId}/monthly

Generate or retrieve statement for a month.

```
GET http://localhost:8080/api/v1/statements/{{ACCOUNT_ID}}/monthly?month=8&year=2026
Authorization: Bearer {{ACCESS_TOKEN}}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "stmt-uuid",
    "accountId": "{{ACCOUNT_ID}}",
    "month": 8,
    "year": 2026,
    "openingBalance": 0.00,
    "closingBalance": 45000.00,
    "totalCredits": 50000.00,
    "totalDebits": 5000.00,
    "transactionCount": 3,
    "status": "GENERATED",
    "pdfKey": "statements/2026/8/{{ACCOUNT_ID}}.pdf",
    "generatedAt": "2026-08-22T11:00:00Z"
  }
}
```

---

#### GET /api/v1/statements/{accountId}/download

Downloads the PDF directly (binary response).

```
GET http://localhost:8080/api/v1/statements/{{ACCOUNT_ID}}/download?month=8&year=2026
Authorization: Bearer {{ACCESS_TOKEN}}
```

In Postman: **Send and Download** — saves the PDF file.

---

### 6.9 Admin Service (Port 8089)

All endpoints require `ROLE_ADMIN`. Admin service proxies requests to downstream services.

```
# All admin endpoints:
Authorization: Bearer {{ADMIN_TOKEN}}
```

#### Customer Management

```
GET  /api/v1/admin/customers?page=0&size=20&search=priya
POST /api/v1/admin/customers/{customerId}/freeze
     Body: { "reason": "Fraud suspicion" }
POST /api/v1/admin/customers/{customerId}/kyc/{kycId}/approve
POST /api/v1/admin/customers/{customerId}/kyc/{kycId}/reject
     Body: { "reason": "Document unclear" }
```

#### Account Management

```
GET  /api/v1/admin/accounts?customerId={{CUSTOMER_ID}}
POST /api/v1/admin/accounts/{accountId}/freeze
     Body: { "reason": "Admin action" }
```

#### Transaction Management

```
GET /api/v1/admin/transactions?accountId=...&type=TRANSFER&status=COMPLETED&dateFrom=2026-08-01&dateTo=2026-08-31
GET /api/v1/admin/transactions/{transactionId}
```

#### Fraud Management

```
GET   /api/v1/admin/fraud/alerts?status=OPEN
PATCH /api/v1/admin/fraud/alerts/{alertId}/resolve
      Body: { "note": "..." }
POST  /api/v1/admin/fraud/blacklist
      Body: { "accountId": "...", "reason": "..." }
```

---

### 6.10 Audit Service (Port 8091)

#### GET /api/v1/audit-logs (Admin/Auditor only)

```
GET http://localhost:8080/api/v1/audit-logs?eventType=customer.registered&page=0&size=50
Authorization: Bearer {{ADMIN_TOKEN}}
```

**Filter params:**
- `eventType` — `customer.registered`, `transaction.completed`, `account.frozen`, etc.
- `actorId` — UUID of who triggered the event
- `entityType` — `Customer`, `Account`, `Transaction`
- `entityId` — UUID of the entity

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "audit-uuid",
        "eventId": "event-uuid",
        "eventType": "customer.registered",
        "topic": "banking.customer.events",
        "actorId": null,
        "entityType": "Customer",
        "entityId": "{{CUSTOMER_ID}}",
        "createdAt": "2026-08-22T10:30:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 7. End-to-End Test Flows

### Flow 1: Full Customer Onboarding

This is the most important flow — must complete before any money movement.

```bash
GW="http://localhost:8080"

# Step 1: Register
REGISTER=$(curl -s -X POST $GW/api/v1/customers/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Priya Sharma",
    "email": "priya@example.com",
    "mobile": "9876543210",
    "password": "Test@1234",
    "dateOfBirth": "1990-05-15"
  }')
echo $REGISTER | python3 -m json.tool
CUSTOMER_ID=$(echo $REGISTER | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['customerId'])")
echo "Customer ID: $CUSTOMER_ID"

# Step 2: Send OTP
curl -s -X POST $GW/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9876543210","purpose":"REGISTRATION"}'
# → Check auth-service logs for the OTP number

# Step 3: Verify OTP (use the OTP from logs)
OTP="123456"  # replace with actual OTP from logs
curl -s -X POST $GW/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d "{\"mobile\":\"9876543210\",\"otp\":\"$OTP\",\"purpose\":\"REGISTRATION\"}"

# Step 4: Verify mobile (advances status to PENDING_KYC)
curl -s -X POST $GW/api/v1/customers/$CUSTOMER_ID/verify-mobile

# Step 5: Login and get tokens
LOGIN=$(curl -s -X POST $GW/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"priya@example.com","password":"Test@1234"}')
TOKEN=$(echo $LOGIN | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "Token: $TOKEN"

# Step 6: Submit KYC
curl -s -X POST $GW/api/v1/customers/$CUSTOMER_ID/kyc \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "AADHAAR",
    "documentNumber": "123456789012",
    "documentUrl": "https://example.com/aadhaar.pdf"
  }'

# Step 7: Approve KYC (need admin token — see below)
# After KYC approval, account-service auto-creates a SAVINGS account via Kafka event

# Step 8: Get the auto-created account
sleep 2  # wait for Kafka processing
ACCOUNTS=$(curl -s "$GW/api/v1/accounts?customerId=$CUSTOMER_ID" \
  -H "Authorization: Bearer $TOKEN")
ACCOUNT_ID=$(echo $ACCOUNTS | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['id'])")
echo "Account ID: $ACCOUNT_ID"
```

**How to get an Admin token:**
The admin user must be seeded in the database. Run this SQL to create an admin credential:
```sql
-- Connect to banking-postgres
docker exec -it banking-postgres psql -U banking -d banking

-- Insert admin user in auth schema
INSERT INTO auth.user_credentials (customer_id, email, mobile, password_hash, status)
VALUES (
  gen_random_uuid(),
  'admin@banking.com',
  '9000000001',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TiGbr9lNL6GkH.8h5.fXY8x7qvIG',  -- password: Admin@1234
  'ACTIVE'
);
```
Then login with `admin@banking.com` / `Admin@1234`.

---

### Flow 2: Deposit & Withdraw

```bash
# Deposit ₹50,000
DEPOSIT=$(curl -s -X POST $GW/api/v1/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":50000,\"description\":\"Salary\"}")
echo $DEPOSIT | python3 -m json.tool

# Check balance
curl -s "$GW/api/v1/accounts/$ACCOUNT_ID/balance" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
# → balance: 50000.00

# Withdraw ₹5,000
curl -s -X POST $GW/api/v1/transactions/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":5000,\"description\":\"Grocery\"}" | python3 -m json.tool

# Try duplicate withdraw with SAME idempotency key — should return cached response
IDEM_KEY=$(uuidgen)
curl -s -X POST $GW/api/v1/transactions/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":1000}" | python3 -m json.tool

curl -s -X POST $GW/api/v1/transactions/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":1000}" | python3 -m json.tool
# → Both return IDENTICAL response (idempotency working)
```

---

### Flow 3: Fund Transfer (Saga)

```bash
# Need two accounts — register a second customer or use the same customer's second account
# For testing, create a second account for same customer:
SECOND_ACCOUNT=$(curl -s -X POST $GW/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"CURRENT\"}")
TO_ACCOUNT=$(echo $SECOND_ACCOUNT | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# Transfer ₹10,000
TRANSFER=$(curl -s -X POST $GW/api/v1/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"fromAccountId\":\"$ACCOUNT_ID\",\"toAccountId\":\"$TO_ACCOUNT\",\"amount\":10000}")
TXN_ID=$(echo $TRANSFER | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Transfer initiated: $TXN_ID"
echo "Status: FRAUD_CHECKING"

# Poll status — should complete in 2-5 seconds
for i in 1 2 3 4 5; do
  sleep 1
  STATUS=$(curl -s "$GW/api/v1/transactions/$TXN_ID" \
    -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
  echo "Attempt $i — Status: $STATUS"
  if [ "$STATUS" = "COMPLETED" ]; then break; fi
done

# Verify balances
echo "From account balance:"
curl -s "$GW/api/v1/accounts/$ACCOUNT_ID/balance" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

echo "To account balance:"
curl -s "$GW/api/v1/accounts/$TO_ACCOUNT/balance" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

**Verify Saga in Kafdrop:**
- Open http://localhost:9100
- Check topics: `banking.transaction.events`, `banking.fraud.events`, `banking.account.events`
- You should see: `transaction.initiated` → `fraud.check.passed` → `account.debited` → `account.credited`

---

### Flow 4: Beneficiary + Transfer

```bash
# Add a beneficiary
BEN=$(curl -s -X POST $GW/api/v1/beneficiaries \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "1234567890123456",
    "ifscCode": "HDFC0001234",
    "beneficiaryName": "Rahul Kumar",
    "bankName": "HDFC Bank"
  }')
BEN_ID=$(echo $BEN | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Beneficiary ID: $BEN_ID"

# Wait for penny-drop (2 seconds in dev)
sleep 3

# Check if transfer allowed
curl -s "$GW/api/v1/beneficiaries/$BEN_ID/verify" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
# → { "transferAllowed": false }  -- cooldown is 24h
# In dev: set beneficiary.cooldown-hours=0 to bypass cooldown

# List all beneficiaries
curl -s "$GW/api/v1/beneficiaries" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

### Flow 5: UPI Transfer

```bash
# Create VPA
VPA=$(curl -s -X POST $GW/api/v1/upi \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"vpa\":\"priya@bank\",\"pin\":\"123456\"}")
UPI_ID=$(echo $VPA | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# UPI transfer (creates another VPA for payee first, or use any VPA string)
curl -s -X POST $GW/api/v1/upi/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "payerVpa": "priya@bank",
    "payeeVpa": "rahul@hdfc",
    "amount": 500,
    "pin": "123456",
    "description": "Coffee"
  }' | python3 -m json.tool

# View UPI transaction history
curl -s "$GW/api/v1/upi/$UPI_ID/transactions" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Change PIN
curl -s -X PUT "$GW/api/v1/upi/$UPI_ID/pin" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPin":"123456","newPin":"999999"}' | python3 -m json.tool
```

---

### Flow 6: Fraud Detection

```bash
# Trigger velocity check — make 11+ transfers quickly (threshold is 10/2h)
for i in $(seq 1 12); do
  curl -s -X POST $GW/api/v1/transactions/deposit \
    -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $(uuidgen)" \
    -H "Content-Type: application/json" \
    -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":100,\"description\":\"Test $i\"}" > /dev/null
  echo "Transaction $i done"
done

# Check fraud alerts (need admin token)
curl -s "$GW/api/v1/fraud/alerts" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

# Trigger large transaction rule (threshold is typically ₹5,00,000)
curl -s -X POST $GW/api/v1/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":600000,\"description\":\"Large deposit\"}" | python3 -m json.tool

# Check MailHog for fraud alert notification
open http://localhost:8025
```

---

### Flow 7: Statement Download

```bash
# Make some transactions first, then:
curl -s "$GW/api/v1/statements/$ACCOUNT_ID/monthly?month=8&year=2026" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Download PDF
curl -s "$GW/api/v1/statements/$ACCOUNT_ID/download?month=8&year=2026" \
  -H "Authorization: Bearer $TOKEN" \
  -o "statement-aug-2026.pdf"

open statement-aug-2026.pdf
```

---

### Flow 8: Admin Operations

```bash
ADMIN_TOKEN="<token from admin login>"

# 1. List all customers
curl -s "$GW/api/v1/admin/customers?page=0&size=10" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

# 2. Approve KYC (get kycId from customer record)
KYC_ID="<kycId from step 6 of onboarding flow>"
curl -s -X POST "$GW/api/v1/admin/customers/$CUSTOMER_ID/kyc/$KYC_ID/approve" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

# 3. Freeze account
curl -s -X POST "$GW/api/v1/admin/accounts/$ACCOUNT_ID/freeze" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Suspicious activity"}' | python3 -m json.tool

# 4. Verify frozen account rejects transactions
curl -s -X POST "$GW/api/v1/transactions/deposit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"$ACCOUNT_ID\",\"amount\":100}" | python3 -m json.tool
# → 400 — account is frozen

# 5. View audit log for this customer
curl -s "$GW/api/v1/audit-logs?entityId=$CUSTOMER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool
```

---

## 8. Verifying Background Systems

### Check Kafka events flowing

```bash
# Watch transaction events in real-time
docker exec banking-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic banking.transaction.events \
  --from-beginning

# Or use Kafdrop UI (much easier)
open http://localhost:9100
```

### Check email notifications (MailHog)

```bash
open http://localhost:8025
# All emails sent by notification-service appear here
```

### Check Redis state

```bash
redis-cli -h localhost -p 6380 -a banking123

# Check balance cache
KEYS balance:*
HGETALL balance:<account-uuid>

# Check token blacklist
KEYS auth:blacklist:*

# Check OTP store
KEYS auth:otp:*

# Check daily UPI limit
KEYS upi:daily:*
```

### Check Prometheus metrics

```bash
open http://localhost:9093
# Query: banking_transactions_total
# Query: banking_fraud_alerts_total
```

### Check Grafana dashboards

```bash
open http://localhost:3000
# Login: admin / admin123
# Dashboards: Banking Overview, Transaction Service, Infrastructure
```

---

## 9. Known Limitations (Dev Mode)

| Limitation | Impact | Workaround |
|-----------|--------|-----------|
| SMS/Push notifications are stubs | SMS and push notifications log only | Check app logs; Email works via MailHog |
| Beneficiary cooldown is 24 hours | Can't immediately transfer to new beneficiary | Set `beneficiary.cooldown-hours=0` in application.yml |
| OTP delivered only via logs | No actual SMS | Check auth-service console for the 6-digit OTP |
| MinIO PDF storage requires MinIO running | Statement PDF fails if MinIO is down | Run `docker-compose up minio` |
| No admin user seeded by default | Admin endpoints return 403 | Manually insert admin credential in DB (see Flow 8) |
| Notification email has placeholder recipient | Emails go to noreply@banking.internal | Check MailHog — the email content is correct |
| Eureka client not configured | Services not auto-discovered | Services use hardcoded URLs — functionally identical |
| Config Service import not configured | Shared config not loaded | Each service's own `application.yml` is the source of truth |

---

## Swagger UI Links

Each service exposes a Swagger UI for interactive API testing:

| Service | Swagger URL |
|---------|-------------|
| Auth | http://localhost:8081/swagger-ui.html |
| Customer | http://localhost:8082/swagger-ui.html |
| Account | http://localhost:8083/swagger-ui.html |
| Transaction | http://localhost:8084/swagger-ui.html |
| Beneficiary | http://localhost:8085/swagger-ui.html |
| UPI | http://localhost:8086/swagger-ui.html |
| Fraud | http://localhost:8090/swagger-ui.html |
| Statement | http://localhost:8088/swagger-ui.html |
| Admin | http://localhost:8089/swagger-ui.html |
| Audit | http://localhost:8091/swagger-ui.html |

> Swagger UI is faster for exploring — no auth header setup needed there.

---

*Last updated: 2026-08-22 | Branch: day-16-28/complete-platform*
