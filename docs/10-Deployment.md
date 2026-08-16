# 10 — Deployment

> **Navigation:** [← Security](09-Security.md) | [UML Diagrams →](11-UML-Diagrams.md)

---

## Table of Contents

1. [Environment Strategy](#1-environment-strategy)
2. [Docker Configuration](#2-docker-configuration)
3. [Docker Compose — Development](#3-docker-compose--development)
4. [Docker Compose — Testing](#4-docker-compose--testing)
5. [GitHub Actions CI/CD Pipeline](#5-github-actions-cicd-pipeline)
6. [Health Checks](#6-health-checks)
7. [Monitoring — Prometheus and Grafana](#7-monitoring--prometheus-and-grafana)
8. [Logging — ELK Stack](#8-logging--elk-stack)
9. [Kubernetes — Production (Future)](#9-kubernetes--production-future)
10. [Autoscaling Strategy](#10-autoscaling-strategy)

---

## 1. Environment Strategy

| Environment | Purpose | Infrastructure | DB | Secrets |
|------------|---------|---------------|----|---------|
| **Development** | Local developer setup | Docker Compose | Single PostgreSQL | `.env` file |
| **Testing (CI)** | Automated tests | GitHub Actions + TestContainers | Ephemeral DB per test run | GitHub Secrets |
| **Staging** | QA validation, pre-prod | Docker Compose on server | Dedicated PostgreSQL | Vault / env vars |
| **Production** | Live banking system | Kubernetes (AWS EKS) | RDS PostgreSQL Multi-AZ | AWS Secrets Manager |

---

## 2. Docker Configuration

### Multi-Stage Dockerfile (per service)

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B      # Cache dependencies layer
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: non-root user
RUN addgroup -S banking && adduser -S banking -G banking
USER banking

# Enable virtual threads (Java 21 Loom)
ENV JAVA_OPTS="-Xms256m -Xmx512m --enable-preview"

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### .dockerignore
```
.git
.idea
target/
*.md
Dockerfile
docker-compose*.yml
```

---

## 3. Docker Compose — Development

```yaml
# docker-compose.yml
version: '3.9'

networks:
  banking-network:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
  kafka-data:
  minio-data:
  prometheus-data:
  grafana-data:

services:

  # ─── Infrastructure ────────────────────────────────────────────────────────

  postgres:
    image: postgres:16-alpine
    container_name: banking-postgres
    environment:
      POSTGRES_USER: banking
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: banking
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U banking"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - banking-network

  redis:
    image: redis:7-alpine
    container_name: banking-redis
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - banking-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: banking-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - banking-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: banking-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true
      KAFKA_NUM_PARTITIONS: 12
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 15s
      timeout: 10s
      retries: 5
    networks:
      - banking-network

  minio:
    image: minio/minio:latest
    container_name: banking-minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
    networks:
      - banking-network

  # ─── Service Discovery & Config ────────────────────────────────────────────

  eureka:
    image: banking/eureka-service:latest
    container_name: banking-eureka
    ports:
      - "8761:8761"
    networks:
      - banking-network

  config-service:
    image: banking/config-service:latest
    container_name: banking-config
    ports:
      - "8888:8888"
    environment:
      GIT_URI: ${CONFIG_GIT_URI}
      GIT_USERNAME: ${CONFIG_GIT_USERNAME}
      GIT_PASSWORD: ${CONFIG_GIT_PASSWORD}
    depends_on:
      - eureka
    networks:
      - banking-network

  # ─── API Gateway ───────────────────────────────────────────────────────────

  api-gateway:
    image: banking/api-gateway:latest
    container_name: banking-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      EUREKA_HOST: eureka
    depends_on:
      redis:
        condition: service_healthy
      eureka:
        condition: service_started
    networks:
      - banking-network

  # ─── Core Services ─────────────────────────────────────────────────────────

  auth-service:
    image: banking/auth-service:latest
    container_name: banking-auth
    ports:
      - "8081:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=auth
      SPRING_DATASOURCE_USERNAME: banking
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
      EUREKA_HOST: eureka
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - banking-network

  customer-service:
    image: banking/customer-service:latest
    container_name: banking-customer
    ports:
      - "8082:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=customer
      KAFKA_BROKERS: kafka:29092
      EUREKA_HOST: eureka
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - banking-network

  account-service:
    image: banking/account-service:latest
    container_name: banking-account
    ports:
      - "8083:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=account
      KAFKA_BROKERS: kafka:29092
      REDIS_HOST: redis
      EUREKA_HOST: eureka
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - banking-network

  transaction-service:
    image: banking/transaction-service:latest
    container_name: banking-transaction
    ports:
      - "8084:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=transaction
      KAFKA_BROKERS: kafka:29092
      REDIS_HOST: redis
      EUREKA_HOST: eureka
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - banking-network

  notification-service:
    image: banking/notification-service:latest
    container_name: banking-notification
    ports:
      - "8089:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=notification
      KAFKA_BROKERS: kafka:29092
      SMTP_HOST: ${SMTP_HOST}
      TWILIO_SID: ${TWILIO_SID}
      TWILIO_TOKEN: ${TWILIO_TOKEN}
      FCM_KEY: ${FCM_KEY}
    networks:
      - banking-network

  fraud-detection-service:
    image: banking/fraud-service:latest
    container_name: banking-fraud
    ports:
      - "8090:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=fraud
      KAFKA_BROKERS: kafka:29092
      REDIS_HOST: redis
    networks:
      - banking-network

  audit-service:
    image: banking/audit-service:latest
    container_name: banking-audit
    ports:
      - "8091:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking?currentSchema=audit
      KAFKA_BROKERS: kafka:29092
    networks:
      - banking-network

  # ─── Observability ─────────────────────────────────────────────────────────

  prometheus:
    image: prom/prometheus:latest
    container_name: banking-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    networks:
      - banking-network

  grafana:
    image: grafana/grafana:latest
    container_name: banking-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    depends_on:
      - prometheus
    networks:
      - banking-network
```

---

## 4. Docker Compose — Testing

```yaml
# docker-compose.test.yml
# Used by GitHub Actions CI — minimal services for integration testing
version: '3.9'

services:
  postgres-test:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
      POSTGRES_DB: testdb
    tmpfs:
      - /var/lib/postgresql/data      # In-memory for speed

  redis-test:
    image: redis:7-alpine
    tmpfs:
      - /data

  kafka-test:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_LISTENERS: PLAINTEXT://:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-test:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true
```

### TestContainers in Integration Tests
```java
@SpringBootTest
@Testcontainers
class TransactionServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

---

## 5. GitHub Actions CI/CD Pipeline

```yaml
# .github/workflows/ci-cd.yml
name: Banking Platform CI/CD

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}/banking

jobs:

  # ─── Test ─────────────────────────────────────────────────────────────────
  test:
    name: Test (${{ matrix.service }})
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - auth-service
          - customer-service
          - account-service
          - transaction-service
          - beneficiary-service
          - upi-service
          - notification-service
          - fraud-detection-service
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Run tests with TestContainers
        run: mvn test -pl services/${{ matrix.service }} -B
        env:
          DOCKER_HOST: unix:///var/run/docker.sock
      
      - name: Publish test report
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Tests - ${{ matrix.service }}
          path: services/${{ matrix.service }}/target/surefire-reports/*.xml
          reporter: java-junit

  # ─── Code Quality ──────────────────────────────────────────────────────────
  code-quality:
    name: Code Quality
    runs-on: ubuntu-latest
    needs: test
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Run OWASP Dependency Check
        run: mvn org.owasp:dependency-check-maven:check -B
      
      - name: SonarQube Analysis
        run: mvn sonar:sonar -B
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}

  # ─── Build Docker Images ───────────────────────────────────────────────────
  build:
    name: Build & Push (${{ matrix.service }})
    runs-on: ubuntu-latest
    needs: [test, code-quality]
    if: github.ref == 'refs/heads/main'
    strategy:
      matrix:
        service:
          - api-gateway
          - auth-service
          - customer-service
          - account-service
          - transaction-service
          - beneficiary-service
          - upi-service
          - notification-service
          - fraud-detection-service
          - statement-service
          - admin-service
          - audit-service
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./services/${{ matrix.service }}
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:latest
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # ─── Deploy to Staging ─────────────────────────────────────────────────────
  deploy-staging:
    name: Deploy to Staging
    runs-on: ubuntu-latest
    needs: build
    environment: staging
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Deploy via Docker Compose on staging server
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.STAGING_HOST }}
          username: ${{ secrets.STAGING_USER }}
          key: ${{ secrets.STAGING_SSH_KEY }}
          script: |
            cd /opt/banking-platform
            git pull origin main
            IMAGE_TAG=${{ github.sha }} docker-compose pull
            IMAGE_TAG=${{ github.sha }} docker-compose up -d --no-deps
            docker-compose ps
```

---

## 6. Health Checks

### Spring Boot Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true            # Kubernetes liveness/readiness probes
  health:
    db:
      enabled: true
    redis:
      enabled: true
    kafka:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Health Check Endpoints

| Endpoint | Purpose | Kubernetes Probe |
|---------|---------|-----------------|
| `/actuator/health` | Overall health | — |
| `/actuator/health/liveness` | Is the JVM alive? | Liveness probe |
| `/actuator/health/readiness` | Is the service ready for traffic? | Readiness probe |
| `/actuator/metrics` | JVM, HTTP, custom metrics | — |
| `/actuator/prometheus` | Prometheus-format metrics | Prometheus scrape |
| `/actuator/info` | Version, build info | — |

### Custom Health Indicator (Kafka)

```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    
    private final KafkaAdmin kafkaAdmin;
    
    @Override
    public Health health() {
        try {
            kafkaAdmin.describeTopics("banking.transaction.events");
            return Health.up()
                .withDetail("kafka", "Available")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "Unavailable: " + e.getMessage())
                .build();
        }
    }
}
```

---

## 7. Monitoring — Prometheus and Grafana

### Prometheus Configuration

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'api-gateway'
    static_configs:
      - targets: ['api-gateway:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'auth-service'
    static_configs:
      - targets: ['auth-service:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'transaction-service'
    static_configs:
      - targets: ['transaction-service:8080']
    metrics_path: '/actuator/prometheus'

  # Repeat for all services...

  - job_name: 'kafka'
    static_configs:
      - targets: ['kafka-exporter:9308']

  - job_name: 'postgresql'
    static_configs:
      - targets: ['postgres-exporter:9187']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

rule_files:
  - "alerts/*.yml"
```

### Alert Rules

```yaml
# monitoring/alerts/banking-alerts.yml
groups:
  - name: banking-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate on {{ $labels.service }}"

      - alert: KafkaConsumerLag
        expr: kafka_consumer_group_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag too high: {{ $value }}"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "DB connection pool > 90% on {{ $labels.service }}"

      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
```

### Grafana Dashboards
- **Banking Overview** — TPS, error rate, p95 latency, active users
- **Transaction Service** — Transfer success rate, fraud block rate, Kafka lag
- **Infrastructure** — PostgreSQL connections, Redis memory, Kafka throughput
- **Security** — Failed login attempts, rate limit hits, fraud alerts per hour

---

## 8. Logging — ELK Stack

### Logstash Pipeline

```
[Service] → [Logstash] → [Elasticsearch] → [Kibana]
```

### Log Format (JSON via Logback)

```xml
<!-- logback-spring.xml -->
<configuration>
  <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>logstash:5000</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"service":"${spring.application.name}", "env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
    </encoder>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="LOGSTASH"/>
  </root>
</configuration>
```

### Log Levels by Environment

| Environment | Root Level | SQL Level |
|------------|-----------|-----------|
| Development | DEBUG | DEBUG (show queries) |
| Testing | INFO | WARN |
| Staging | INFO | WARN |
| Production | WARN | ERROR |

---

## 9. Kubernetes — Production (Future)

### Resource Requests/Limits (per service instance)

| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---------|------------|----------|----------------|-------------|
| API Gateway | 250m | 1000m | 256Mi | 512Mi |
| Auth Service | 250m | 500m | 256Mi | 512Mi |
| Transaction Service | 500m | 2000m | 512Mi | 1Gi |
| Account Service | 500m | 1000m | 512Mi | 1Gi |
| Notification Service | 100m | 500m | 256Mi | 512Mi |
| Fraud Detection | 500m | 2000m | 512Mi | 1Gi |
| Audit Service | 100m | 500m | 256Mi | 512Mi |

### Kubernetes Manifest (Transaction Service excerpt)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transaction-service
  namespace: banking
spec:
  replicas: 3
  selector:
    matchLabels:
      app: transaction-service
  template:
    spec:
      containers:
        - name: transaction-service
          image: ghcr.io/banking/transaction-service:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: banking-secrets
                  key: postgres-password
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 2000m
              memory: 1Gi
```

---

## 10. Autoscaling Strategy

### Horizontal Pod Autoscaler (Kubernetes)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transaction-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: transaction-service
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: External
      external:
        metric:
          name: kafka_consumer_group_lag
          selector:
            matchLabels:
              group: transaction-service-saga-cg
        target:
          type: Value
          value: 1000             # Scale up when lag > 1000 messages
```

---

> **Next:** [UML Diagrams →](11-UML-Diagrams.md)
