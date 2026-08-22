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

# Fraud events
create_topic "banking.fraud.events" 6

echo ""
echo "All topics created. Listing:"
kafka-topics --bootstrap-server "$BROKER" --list | sort