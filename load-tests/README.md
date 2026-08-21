# Load Tests

k6 load test scripts for the Banking System API.

## Prerequisites

Install k6: https://k6.io/docs/get-started/installation/

```bash
# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows
winget install k6
```

## Environment Variables

| Variable       | Description                          | Default                 |
|----------------|--------------------------------------|-------------------------|
| `BASE_URL`     | API Gateway base URL                 | `http://localhost:8080` |
| `TOKEN`        | JWT Bearer token for authentication  | (empty)                 |
| `ACCOUNT_ID`   | Account ID for balance inquiry tests | (empty)                 |
| `FROM_ACCOUNT` | Source account ID for transfers      | (empty)                 |
| `TO_ACCOUNT`   | Destination account ID for transfers | (empty)                 |

## Running the Tests

### Balance Inquiry Load Test

Ramps up to 100 virtual users over 2 minutes, with a p95 latency threshold of 200ms and error rate below 1%.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=<your-jwt-token> \
  -e ACCOUNT_ID=<account-uuid> \
  load-tests/balance-inquiry.js
```

### Transfer Load Test

Ramps up to 50 virtual users over 2 minutes, with a p95 latency threshold of 500ms. Each virtual user generates a unique idempotency key per request.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=<your-jwt-token> \
  -e FROM_ACCOUNT=<source-account-uuid> \
  -e TO_ACCOUNT=<destination-account-uuid> \
  load-tests/transfer.js
```

### Running Against a Remote Environment

```bash
k6 run \
  -e BASE_URL=https://api.banking.example.com \
  -e TOKEN=<your-jwt-token> \
  -e ACCOUNT_ID=<account-uuid> \
  --out json=results.json \
  load-tests/balance-inquiry.js
```

## Viewing Results in Grafana

To stream results to Prometheus/Grafana, run k6 with the Prometheus remote write output:

```bash
k6 run \
  --out experimental-prometheus-rw \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=<your-jwt-token> \
  -e ACCOUNT_ID=<account-uuid> \
  load-tests/balance-inquiry.js
```
