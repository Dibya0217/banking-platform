-- ============================================================
-- Banking Platform — Database Initialization
-- Run once when PostgreSQL container first starts.
-- Creates schemas for each microservice.
-- ============================================================

-- Each microservice owns exactly one schema.
-- In production, each service gets its own DB instance.

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS customer;
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS transaction;
CREATE SCHEMA IF NOT EXISTS beneficiary;
CREATE SCHEMA IF NOT EXISTS upi;
CREATE SCHEMA IF NOT EXISTS fraud;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS statement;

-- Grant the banking user full access to all schemas
GRANT ALL PRIVILEGES ON SCHEMA auth TO banking;
GRANT ALL PRIVILEGES ON SCHEMA customer TO banking;
GRANT ALL PRIVILEGES ON SCHEMA account TO banking;
GRANT ALL PRIVILEGES ON SCHEMA transaction TO banking;
GRANT ALL PRIVILEGES ON SCHEMA beneficiary TO banking;
GRANT ALL PRIVILEGES ON SCHEMA upi TO banking;
GRANT ALL PRIVILEGES ON SCHEMA fraud TO banking;
GRANT ALL PRIVILEGES ON SCHEMA notification TO banking;
GRANT ALL PRIVILEGES ON SCHEMA audit TO banking;
GRANT ALL PRIVILEGES ON SCHEMA statement TO banking;

-- Default search path for the banking user
ALTER USER banking SET search_path TO public, auth, customer, account, transaction, beneficiary, upi, fraud, notification, audit, statement;

-- Enable pgcrypto for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Audit log schema: restricted to INSERT + SELECT only.
-- The application DB user must NOT be able to UPDATE or DELETE audit records.
-- Enforced at DB level via a separate audit_writer role (production).
-- In dev, the banking user is used for simplicity.

SELECT 'Database initialized successfully' AS status;
