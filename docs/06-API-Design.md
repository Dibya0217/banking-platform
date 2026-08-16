# 06 — API Design

> **Navigation:** [← Database Design](05-Database-Design.md) | [Kafka Design →](07-Kafka-Design.md)

---

## Table of Contents

1. [API Conventions](#1-api-conventions)
2. [Auth Service APIs](#2-auth-service-apis)
3. [Customer Service APIs](#3-customer-service-apis)
4. [Account Service APIs](#4-account-service-apis)
5. [Transaction Service APIs](#5-transaction-service-apis)
6. [Beneficiary Service APIs](#6-beneficiary-service-apis)
7. [UPI Service APIs](#7-upi-service-apis)
8. [Statement Service APIs](#8-statement-service-apis)
9. [Admin Service APIs](#9-admin-service-apis)
10. [Error Response Format](#10-error-response-format)
11. [Common HTTP Status Codes](#11-common-http-status-codes)

---

## 1. API Conventions

### Base URL
```
Production:   https://api.bankingplatform.com
Development:  http://localhost:8080
```

### Versioning
All APIs are URL-versioned: `/api/v1/`. Breaking changes are released under `/api/v2/`.

### Authentication
All APIs (except login, register, OTP send) require:
```
Authorization: Bearer <accessToken>
```

### Idempotency
All POST/PUT mutation APIs accept:
```
Idempotency-Key: <client-generated-UUID>
```
Repeat requests with the same key return the original response.

### Pagination
```
GET /api/v1/transactions?page=0&size=20&sort=createdAt,desc
```
Response includes:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 145,
  "totalPages": 8,
  "last": false
}
```

### Standard Response Envelope
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-08-15T10:30:00Z",
  "correlationId": "req-uuid"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Account balance is insufficient for this transaction",
    "details": []
  },
  "timestamp": "2026-08-15T10:30:00Z",
  "correlationId": "req-uuid"
}
```

---

## 2. Auth Service APIs

### POST /api/v1/auth/login
**Description:** Authenticate with email and password.

**Auth:** None (public)

**Request:**
```json
{
  "email": "priya@example.com",
  "password": "SecureP@ssw0rd"
}
```

**Validation:**
- `email`: required, valid email format
- `password`: required, min 8 characters

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `ACCOUNT_LOCKED` | 423 | Too many failed attempts |
| `ACCOUNT_DISABLED` | 403 | Account deactivated |

---

### POST /api/v1/auth/refresh
**Description:** Get a new access token using refresh token.

**Auth:** None (carries refresh token in body)

**Request:**
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Errors:** `TOKEN_EXPIRED` (401), `TOKEN_INVALID` (401)

---

### POST /api/v1/auth/logout
**Description:** Invalidate access and refresh tokens.

**Auth:** Bearer token

**Response:** 204 No Content

---

### POST /api/v1/auth/otp/send
**Auth:** None

**Request:**
```json
{ "mobile": "9876543210", "purpose": "REGISTRATION" }
```

**Response 200:**
```json
{ "message": "OTP sent successfully", "expiresIn": 300 }
```

**Rate limit:** 5 OTP sends per mobile per hour.

---

### POST /api/v1/auth/otp/verify
**Auth:** None

**Request:**
```json
{ "mobile": "9876543210", "otp": "482910", "purpose": "REGISTRATION" }
```

**Response 200:**
```json
{ "verified": true, "verificationToken": "<short-lived-token>" }
```

---

## 3. Customer Service APIs

### POST /api/v1/customers/register
**Description:** Register a new customer.

**Auth:** None (public)

**Request:**
```json
{
  "fullName": "Priya Sharma",
  "email": "priya@example.com",
  "mobile": "9876543210",
  "password": "SecureP@ssw0rd",
  "dateOfBirth": "1990-05-15",
  "gender": "FEMALE"
}
```

**Validation:**
- `fullName`: required, 2-100 chars
- `email`: required, unique, valid format
- `mobile`: required, 10-digit Indian mobile, unique
- `password`: required, min 8 chars, must contain uppercase, digit, special char
- `dateOfBirth`: required, customer must be ≥ 18 years

**Response 201:**
```json
{
  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "message": "Registration successful. OTP sent to 9876543210.",
  "status": "PENDING_VERIFICATION"
}
```

**Errors:** `DUPLICATE_EMAIL` (409), `DUPLICATE_MOBILE` (409), `UNDER_AGE` (400)

---

### GET /api/v1/customers/{customerId}
**Auth:** Bearer | Roles: CUSTOMER (own only), ADMIN

**Response 200:**
```json
{
  "id": "aaaaaaaa-...",
  "fullName": "Priya Sharma",
  "email": "priya@example.com",
  "mobile": "98765XXXXX",
  "status": "ACTIVE",
  "kycStatus": "APPROVED",
  "createdAt": "2026-08-01T09:00:00Z"
}
```

**Note:** Mobile number is masked for CUSTOMER role; unmasked for ADMIN.

---

### PUT /api/v1/customers/{customerId}
**Auth:** Bearer | Roles: CUSTOMER (own only)

**Request (partial update):**
```json
{
  "addressLine1": "123 MG Road",
  "city": "Bangalore",
  "state": "Karnataka",
  "pincode": "560001"
}
```

**Response 200:** Updated customer object

---

### POST /api/v1/customers/{customerId}/kyc
**Auth:** Bearer | Roles: CUSTOMER

**Request (multipart/form-data):**
```
documentType: AADHAAR
documentNumber: 1234-5678-9012
documentFile: <binary>
```

**Response 202:**
```json
{
  "kycId": "uuid",
  "status": "PENDING",
  "message": "KYC submitted. Review takes 1-2 business days."
}
```

---

### POST /api/v1/customers/{customerId}/freeze
**Auth:** Bearer | Roles: ADMIN

**Request:**
```json
{ "reason": "Suspicious activity detected" }
```

**Response 200:**
```json
{ "status": "FROZEN", "frozenAt": "2026-08-15T10:00:00Z" }
```

---

## 4. Account Service APIs

### POST /api/v1/accounts
**Auth:** Bearer | Roles: CUSTOMER

**Request:**
```json
{
  "customerId": "aaaaaaaa-...",
  "accountType": "SAVINGS"
}
```

**Validation:**
- Customer must have `ACTIVE` status (KYC approved)
- Max 3 accounts per customer

**Response 201:**
```json
{
  "accountId": "bbbbbbbb-...",
  "accountNumber": "2026081500001234",
  "accountType": "SAVINGS",
  "ifscCode": "BANK0000001",
  "balance": 0.00,
  "status": "ACTIVE"
}
```

---

### GET /api/v1/accounts/{accountId}/balance
**Auth:** Bearer | Roles: CUSTOMER (own), ADMIN

**Response 200:**
```json
{
  "accountId": "bbbbbbbb-...",
  "accountNumber": "XXXXXX1234",
  "balance": 50000.00,
  "currency": "INR",
  "availableBalance": 49000.00,
  "minimumBalance": 1000.00,
  "asOf": "2026-08-15T10:30:00Z"
}
```

**Cache:** Served from Redis if available (max 30 sec stale). `Cache-Control: max-age=30`

---

### GET /api/v1/accounts
**Auth:** Bearer | Roles: CUSTOMER

**Query params:** `customerId` (optional for admin)

**Response 200:** Array of account summary objects

---

### POST /api/v1/accounts/{accountId}/freeze
**Auth:** Bearer | Roles: ADMIN

**Request:**
```json
{ "reason": "Court order #12345", "freezeType": "FULL" }
```

`freezeType`: `FULL` | `DEBIT_ONLY` | `CREDIT_ONLY`

**Response 200:** Account status updated

---

### DELETE /api/v1/accounts/{accountId}
**Auth:** Bearer | Roles: CUSTOMER (own), ADMIN

**Business Rules:**
- Account balance must be zero
- No pending transactions

**Response 200:**
```json
{ "status": "CLOSED", "closedAt": "2026-08-15T10:30:00Z" }
```

---

## 5. Transaction Service APIs

### POST /api/v1/transactions/deposit
**Auth:** Bearer | Roles: CUSTOMER, ADMIN

**Headers:** `Idempotency-Key: <UUID>`

**Request:**
```json
{
  "accountId": "bbbbbbbb-...",
  "amount": 10000.00,
  "description": "Cash deposit",
  "channel": "BRANCH"
}
```

**Response 202:**
```json
{
  "transactionId": "cccccccc-...",
  "status": "PENDING",
  "amount": 10000.00,
  "referenceNumber": "REF20260815001"
}
```

---

### POST /api/v1/transactions/withdraw
**Auth:** Bearer | Roles: CUSTOMER

**Headers:** `Idempotency-Key: <UUID>`

**Request:**
```json
{
  "accountId": "bbbbbbbb-...",
  "amount": 5000.00,
  "description": "ATM withdrawal"
}
```

**Validation:**
- Amount ≤ account balance
- Amount ≤ daily debit limit remaining
- Account status = ACTIVE (not FROZEN)

**Errors:** `INSUFFICIENT_FUNDS` (422), `DAILY_LIMIT_EXCEEDED` (422), `ACCOUNT_FROZEN` (423)

---

### POST /api/v1/transactions/transfer
**Auth:** Bearer | Roles: CUSTOMER

**Headers:** `Idempotency-Key: <UUID>`

**Request:**
```json
{
  "fromAccountId": "bbbbbbbb-...",
  "toAccountId": "dddddddd-...",
  "amount": 5000.00,
  "description": "Rent payment",
  "scheduledAt": null
}
```

**Response 202:**
```json
{
  "transactionId": "cccccccc-...",
  "status": "PENDING",
  "message": "Transfer initiated. Processing in progress.",
  "estimatedCompletion": "2026-08-15T10:30:05Z"
}
```

---

### POST /api/v1/transactions/{transactionId}/reverse
**Auth:** Bearer | Roles: ADMIN

**Request:**
```json
{ "reason": "Customer request - accidental transfer" }
```

**Business Rules:**
- Reversal allowed only within T+1 business day
- Transaction must be in `COMPLETED` status

---

### GET /api/v1/transactions/{transactionId}
**Auth:** Bearer | Roles: CUSTOMER (own), ADMIN

**Response 200:**
```json
{
  "id": "cccccccc-...",
  "type": "TRANSFER",
  "amount": 5000.00,
  "currency": "INR",
  "status": "COMPLETED",
  "fromAccount": "XXXXXX1234",
  "toAccount": "XXXXXX5678",
  "description": "Rent payment",
  "referenceNumber": "REF20260815001",
  "completedAt": "2026-08-15T10:30:05Z"
}
```

---

### GET /api/v1/transactions
**Auth:** Bearer | Roles: CUSTOMER, ADMIN

**Query params:**
```
accountId     UUID      required
page          int       default=0
size          int       default=20, max=100
sort          string    default=createdAt,desc
dateFrom      ISO date  optional
dateTo        ISO date  optional
type          enum      DEPOSIT|WITHDRAWAL|TRANSFER
minAmount     decimal   optional
maxAmount     decimal   optional
status        enum      optional
```

**Response 200:** Paginated transaction list

---

## 6. Beneficiary Service APIs

### POST /api/v1/beneficiaries
**Auth:** Bearer | Roles: CUSTOMER

**Request:**
```json
{
  "accountNumber": "1234567890",
  "ifscCode": "HDFC0001234",
  "beneficiaryName": "Rahul Verma",
  "nickName": "Rahul",
  "bankName": "HDFC Bank"
}
```

**Validation:**
- IFSC code: 11-char format `[A-Z]{4}0[A-Z0-9]{6}`
- Max 20 beneficiaries per customer
- Cannot add own account as beneficiary

**Response 202:**
```json
{
  "beneficiaryId": "uuid",
  "status": "PENDING_VERIFICATION",
  "message": "Penny-drop verification initiated. Beneficiary will be active in ~2 minutes."
}
```

---

### DELETE /api/v1/beneficiaries/{beneficiaryId}
**Auth:** Bearer | Roles: CUSTOMER

**Response 200:** Beneficiary soft-deleted

---

### GET /api/v1/beneficiaries
**Auth:** Bearer | Roles: CUSTOMER

**Response 200:** List of active beneficiaries

---

### GET /api/v1/beneficiaries/{beneficiaryId}/verify
**Auth:** Bearer | Roles: CUSTOMER

**Response 200:**
```json
{
  "beneficiaryId": "uuid",
  "status": "ACTIVE",
  "verifiedName": "Rahul Verma",
  "transferEnabledAt": "2026-08-16T10:00:00Z"
}
```

---

## 7. UPI Service APIs

### POST /api/v1/upi
**Auth:** Bearer | Roles: CUSTOMER

**Request:**
```json
{
  "accountId": "bbbbbbbb-...",
  "vpa": "priya@bankname",
  "pin": "123456"
}
```

**Validation:**
- VPA format: `[a-z0-9.]+@[a-z]+`
- PIN: exactly 6 digits
- One VPA per account

**Response 201:**
```json
{
  "upiId": "uuid",
  "vpa": "priya@bankname",
  "status": "ACTIVE",
  "dailyLimit": 100000.00
}
```

---

### PUT /api/v1/upi/{upiId}/pin
**Auth:** Bearer | Roles: CUSTOMER

**Request:**
```json
{
  "currentPin": "123456",
  "newPin": "789012"
}
```

---

### POST /api/v1/upi/transfer
**Auth:** Bearer | Roles: CUSTOMER

**Headers:** `Idempotency-Key: <UUID>`

**Request:**
```json
{
  "payerVpa": "priya@bankname",
  "payeeVpa": "rahul@hdfc",
  "amount": 500.00,
  "pin": "123456",
  "remarks": "Lunch split"
}
```

**Validation:**
- PIN verified (BCrypt)
- Daily limit check
- Amount > 0, ≤ ₹1,00,000 per transaction

**Response 202:** Transaction initiated

---

### GET /api/v1/upi/{upiId}/transactions
**Auth:** Bearer | Roles: CUSTOMER

**Query params:** `page`, `size`, `dateFrom`, `dateTo`

**Response 200:** Paginated UPI transaction list

---

## 8. Statement Service APIs

### GET /api/v1/statements/{accountId}/monthly
**Auth:** Bearer | Roles: CUSTOMER (own), ADMIN

**Query params:**
```
month   int  required (1-12)
year    int  required
```

**Response 200:**
```json
{
  "accountId": "bbbbbbbb-...",
  "accountNumber": "XXXXXX1234",
  "month": 8,
  "year": 2026,
  "openingBalance": 45000.00,
  "closingBalance": 50000.00,
  "totalCredits": 25000.00,
  "totalDebits": 20000.00,
  "transactions": [...]
}
```

---

### GET /api/v1/statements/{accountId}/download
**Auth:** Bearer | Roles: CUSTOMER (own), ADMIN

**Query params:** `month`, `year`

**Response 200:**
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="statement-ACC-2026-08.pdf"
<binary PDF data>
```

---

## 9. Admin Service APIs

### GET /api/v1/admin/customers
**Auth:** Bearer | Roles: ADMIN, OPS, AUDITOR

**Query params:** `status`, `page`, `size`, `search` (name/email/mobile)

**Response 200:** Paginated customer list with full details

---

### POST /api/v1/admin/customers/{customerId}/kyc/{kycId}/approve
**Auth:** Bearer | Roles: ADMIN

**Response 200:** KYC approved; triggers account creation event

---

### POST /api/v1/admin/customers/{customerId}/kyc/{kycId}/reject
**Auth:** Bearer | Roles: ADMIN

**Request:**
```json
{ "reason": "Document blurry, please resubmit" }
```

---

### GET /api/v1/admin/fraud-alerts
**Auth:** Bearer | Roles: ADMIN, AUDITOR

**Query params:** `status`, `severity`, `page`, `size`, `dateFrom`, `dateTo`

---

### POST /api/v1/admin/fraud-alerts/{alertId}/resolve
**Auth:** Bearer | Roles: ADMIN

**Request:**
```json
{ "resolution": "FALSE_POSITIVE", "notes": "Customer confirmed legit transaction" }
```

---

### GET /api/v1/admin/audit-logs
**Auth:** Bearer | Roles: AUDITOR, SUPER_ADMIN

**Query params:** `entityType`, `entityId`, `performedBy`, `dateFrom`, `dateTo`, `page`, `size`

---

## 10. Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "message": "must be a valid email address" },
      { "field": "password", "message": "must contain at least one uppercase letter" }
    ]
  },
  "timestamp": "2026-08-15T10:30:00Z",
  "correlationId": "req-uuid",
  "path": "/api/v1/customers/register"
}
```

---

## 11. Common HTTP Status Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET, PUT |
| 201 | Created | Successful resource creation (account, customer) |
| 202 | Accepted | Async operation initiated (transfer, statement generation) |
| 204 | No Content | Successful DELETE, logout |
| 400 | Bad Request | Malformed request, missing required fields |
| 401 | Unauthorized | Missing or invalid JWT |
| 403 | Forbidden | Valid JWT but insufficient role |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource (email, VPA) |
| 422 | Unprocessable Entity | Business rule violation (insufficient funds, daily limit) |
| 423 | Locked | Account frozen |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |
| 503 | Service Unavailable | Downstream service unavailable (circuit breaker open) |

---

> **Next:** [Kafka Design →](07-Kafka-Design.md)
