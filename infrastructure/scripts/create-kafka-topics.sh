#!/bin/bash
# Creates all Kafka topics with the correct partition counts.
# Run after Kafka is healthy:
#   docker exec banking-kafka bash /scripts/create-kafka-topics.sh

set -e

BROKER="localhost:9092"
REPLICATION=1   # 1 for local dev; 3 in production

echo "Creating Kafka topics..."

create_topic() {
  local topic=$1
  local partitions=$2
  kafka-topics --bootstrap-server "$BROKER" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION"
  echo "  Created: $topic (partitions=$partitions)"
}

# Customer events — moderate throughput
create_topic "banking.customer.events" 6

# Account events — higher throughput (balance updates)
create_topic "banking.account.events" 12

# Transaction events — highest throughput (core business)
create_topic "banking.transaction.events" 24

# UPI events
create_topic "banking.upi.events" 12

# Fraud events
create_topic "banking.fraud.events" 6

# Notification events
create_topic "banking.notification.events" 6

# Admin events
create_topic "banking.admin.events" 3

# Dead Letter Queues (DLQ) — 1 partition each, 30-day retention
create_topic "banking.customer.events.dlq" 1
create_topic "banking.account.events.dlq" 1
create_topic "banking.transaction.events.dlq" 1
create_topic "banking.upi.events.dlq" 1
create_topic "banking.fraud.events.dlq" 1
create_topic "banking.notification.events.dlq" 1

echo ""
echo "All topics created. Listing:"
kafka-topics --bootstrap-server "$BROKER" --list | sort