# High-Level Design (HLD) - TeleTrack360

## 1. System Overview

TeleTrack360 is a distributed incident management platform for telecom operations teams. The system migrates from a legacy monolithic application to a microservices architecture with event-driven communication, polyglot persistence, and comprehensive observability.

### Business Goals
- Handle millions of incident requests across multiple regions
- Provide real-time notifications and analytics
- Ensure 99.5% uptime with fault tolerance
- Enable AI-powered incident pattern analysis
- Maintain complete audit trails for compliance

---

## 2. Architecture Style

**Selected Architecture:** Microservices with Event-Driven Architecture (EDA)

**Key Architectural Patterns:**
- API Gateway Pattern (single entry point)
- Service Registry Pattern (Eureka for service discovery)
- Event Sourcing (incident state changes)
- CQRS (Command Query Responsibility Segregation)
- Circuit Breaker Pattern (resilience)
- Externalized Configuration (Spring Cloud Config)

---

## 3. System Context Diagram

![Architecture](imgs/sys-architecture.png)

**External Actors:**
- **End Users:** Support staff, operators who create and manage incidents
- **Administrators:** System admins who manage users and configurations
- **External Systems:** Email providers, SMS gateways, monitoring dashboards
- **AI Service:** Python-based ML service for pattern analysis

**System Boundary:**
- All requests enter through API Gateway
- Authentication handled at gateway level
- Services communicate internally via REST (Feign) and Kafka

---

## 4. Container Diagram

![container-diag.png](imgs%2Fcontainer-diag.png)

**Service Containers:**

| Service | Technology | Port | Responsibility |
|---------|-----------|------|----------------|
| Discovery Service | Spring Cloud Eureka | 8761 | Service registry and health checks |
| Config Service | Spring Cloud Config | 8888 | Centralized configuration management |
| API Gateway | Spring Cloud Gateway | 8080 | Routing, auth, rate limiting, circuit breaking |
| User Service | Spring Boot + PostgreSQL | 8081 | User management, authentication, authorization |
| Incident Service | Spring Boot + PostgreSQL + MongoDB | 8082 | Core domain, CRUD operations, event sourcing |
| Notification Service | Spring Boot + PostgreSQL | 8083 | Event-driven notifications (email, SMS, Slack) |
| Reporting Service | Spring Boot + MongoDB + Redis | 8084 | Analytics, aggregations, report generation |
| AI Service | Flask (Python) | 8085 | Incident pattern analysis and summarization |

**Data Stores:**

| Store | Technology | Purpose |
|-------|-----------|---------|
| PostgreSQL | Relational DB | Transactional data (users, incidents, notifications) |
| MongoDB | Document DB | Analytics data (incident history, reports) |
| Redis | In-Memory Cache | Report caching (1-hour TTL) |
| Kafka | Message Broker | Asynchronous event streaming |

---

## 5. Communication Architecture

### 5.1 Synchronous Communication (REST via Feign)

**Feign Client Usage:**
- **User Service ← Incident Service:** Validate user existence when creating incidents
- **User Service ← Notification Service:** Fetch user details for notifications
- **Incident Service ← Reporting Service:** Fetch incident metadata for reports
- **AI Service ← Reporting Service:** Request pattern analysis

**Service-to-Service Security Strategy:**

Since user JWT tokens cannot be reused for service-to-service calls, we use **API Key Authentication**:

**Implementation:**

1. **Configuration (in Config Service):**
    - Each service has a unique API key stored in centralized config
    - Format: `X-Service-Key: sk_{service-name}_{random-string}`
    - Example: `sk_incident_xyz123abc456`

2. **Feign Request Interceptor (in calling service):**
    - Automatically adds `X-Service-Key` header to all outgoing requests
    - Key fetched from service's own configuration

3. **Validation Filter (in target service):**
    - Checks incoming `X-Service-Key` header
    - Validates against whitelist of allowed service keys
    - Rejects requests without valid service key

4. **Audit Logging:**
    - All service-to-service calls logged with: timestamp, caller, target, endpoint, status
    - Stored in audit tables for compliance

**Why API Key over OAuth2 for service-to-service?**
- Simpler implementation (no token endpoint needed)
- No token expiry management overhead
- Sufficient for internal, trusted service communication
- Easier to debug and maintain for MVP

### 5.2 Asynchronous Communication (Kafka)

**Kafka Topic Design:**

| Topic Name | Producer | Consumers | Message Schema | Purpose |
|-----------|----------|-----------|---------------|---------|
| `incident.created` | Incident Service | Notification, Reporting | IncidentCreatedEvent | Trigger notifications, update analytics |
| `incident.updated` | Incident Service | Notification, Reporting | IncidentUpdatedEvent | Notify status changes, sync MongoDB |
| `incident.assigned` | Incident Service | Notification | IncidentAssignedEvent | Notify assigned user |
| `incident.resolved` | Incident Service | Notification, Reporting | IncidentResolvedEvent | Notify resolution, calculate metrics |
| `incident.closed` | Incident Service | Reporting | IncidentClosedEvent | Final analytics sync |

**Event Message Structure (JSON):**
```json
{
  "eventId": "uuid",
  "eventType": "INCIDENT_CREATED",
  "timestamp": "2026-01-09T15:30:45.123Z",
  "correlationId": "trace-id-abc-123",
  "payload": {
    "incidentId": "uuid",
    "title": "Network outage in region A",
    "priority": "HIGH",
    "category": "NETWORK",
    "createdBy": "user-uuid"
  }
}
```

**Kafka Configuration:**
- **Partitions:** 3 per topic (enables parallel consumer processing)
- **Replication Factor:** 1 (sufficient for dev/demo environment)
- **Consumer Groups:** Each service type has its own consumer group
- **Retry Policy:** Max 3 retries with exponential backoff (1s, 2s, 4s)
- **Error Handling:** Dead Letter Queue (DLQ) for permanently failed messages

---

## 6. Data Architecture

### 6.1 Polyglot Persistence Strategy

**Database Selection Justification:**

| Database | Services | Data Characteristics | Rationale |
|----------|----------|---------------------|-----------|
| **PostgreSQL** | User, Incident, Notification | Structured, relational, ACID required | Strong consistency for authentication, incident transactions, referential integrity |
| **MongoDB** | Incident (history), Reporting | Semi-structured, read-heavy, flexible schema | Fast aggregation pipelines, denormalized analytics data, no joins needed |
| **Redis** | Reporting | Key-value, volatile, high-speed | Ultra-fast caching for frequently accessed reports, reduces MongoDB load |

### 6.2 Data Flow Patterns

**Write Path (Incident Creation):**
```
1. User → API Gateway → Incident Service
2. Incident Service writes to PostgreSQL (incidents table) - source of truth
3. Incident Service writes event to PostgreSQL (incident_events table) - event sourcing
4. Incident Service publishes event to Kafka (incident.created topic)
5. Incident Service returns response to user (synchronous)
6. [Async] Notification Service consumes Kafka event → sends notifications
7. [Async] Reporting Service consumes Kafka event → syncs to MongoDB
```

**Read Path (Report Generation):**
```
1. User → API Gateway → Reporting Service
2. Reporting Service checks Redis cache (key: report:{type}:{date})
3. If cache HIT: Return cached report (fast path)
4. If cache MISS: 
   - Query MongoDB with aggregation pipeline
   - Store result in Redis (TTL: 1 hour)
   - Return computed report
```

### 6.3 Event Sourcing Pattern


**Event Sourcing Implementation:**
- **Storage:** `incident_events` table in PostgreSQL
- **Characteristics:** Append-only, immutable log
- **Event Structure:**
    - Event ID (UUID)
    - Incident ID (FK to incidents)
    - Event Type (CREATED, UPDATED, ASSIGNED, RESOLVED, CLOSED)
    - Performed By (user ID)
    - Payload (JSONB - full event data)
    - Timestamp
    - Correlation ID (for tracing)

**Benefits:**
- Complete audit trail (who, what, when)
- Ability to replay events to reconstruct incident state
- Temporal queries (what was the state at time T?)
- Debug capability (trace all changes)
- Compliance with regulations

**Event Types:**
- `CREATED`: Initial incident creation
- `UPDATED`: Status, priority, description changes
- `ASSIGNED`: Incident assigned to user
- `RESOLVED`: Incident marked as resolved (timestamp recorded)
- `CLOSED`: Incident marked as closed (final state)
- `COMMENTED`: User added comment

---

## 7. Security Architecture

### 7.1 Authentication & Authorization Flow

**OAuth2 + JWT Strategy:**

**User Authentication Flow:**
```
Step 1: User Login
  POST /api/v1/auth/login
  Body: { "username": "john.doe", "password": "secret" }
  
Step 2: User Service validates credentials
  - Query users table
  - Compare BCrypt password hash
  - Check isActive and isApproved flags
  
Step 3: Generate JWT
  Header: { "alg": "HS512", "typ": "JWT" }
  Payload: {
    "sub": "user-uuid-123",
    "username": "john.doe",
    "role": "OPERATOR",
    "scopes": ["read", "write"],
    "iat": 1704805845,
    "exp": 1704806745  // 15 minutes
  }
  Signature: HMAC-SHA512(header + payload, secret)
  
Step 4: Return JWT to user
  Response: {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "expiresAt": "2026-01-09T15:45:45Z"
  }
  
Step 5: User includes JWT in subsequent requests
  Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**API Gateway JWT Validation:**
```
1. Extract token from Authorization header
2. Verify signature using shared secret key
3. Check expiration timestamp
4. Extract claims (userId, role, scopes)
5. Add user context to request headers:
   - X-User-Id: extracted user ID
   - X-User-Role: extracted role
   - X-User-Scopes: extracted scopes
6. Forward request to target service
```

**Service-Level Authorization:**
- Services trust headers from Gateway (Gateway is security boundary)
- Additional validation using Spring Security `@PreAuthorize` annotations
- Example: `@PreAuthorize("hasAuthority('SCOPE_write') and hasRole('OPERATOR')")`

### 7.2 RBAC Matrix


| Permission | ADMIN | OPERATOR | SUPPORT | Scope Required |
|-----------|-------|----------|---------|----------------|
| User Management (CRUD) | ✓ | ✗ | ✗ | admin |
| User Approval | ✓ | ✗ | ✗ | admin |
| Incident Create | ✓ | ✓ | ✓ | write |
| Incident Update | ✓ | ✓ | ✗ | write |
| Incident Assign | ✓ | ✓ | ✗ | write |
| Incident Resolve/Close | ✓ | ✓ | ✗ | write |
| View Incidents | ✓ | ✓ | ✓ | read |
| View Reports | ✓ | ✓ | ✓ | read |
| System Configuration | ✓ | ✗ | ✗ | admin |

### 7.3 Service-to-Service Security (API Key)


**API Key Management:**

**Storage (Config Service - application.yml):**
```yaml
service:
  security:
    keys:
      incident-service: "sk_incident_xyz123abc456def789"
      notification-service: "sk_notification_def789ghi012jkl345"
      reporting-service: "sk_reporting_jkl345mno678pqr901"
```

**Feign Configuration (in calling service):**
- Feign Request Interceptor automatically adds header to all outgoing requests
- Key loaded from service's config on startup
- Header: `X-Service-Key: sk_incident_xyz123abc456def789`

**Validation (in target service):**
- Filter checks for `X-Service-Key` header
- Validates against whitelist of allowed keys
- Logs all service-to-service requests for audit
- Rejects requests without valid key (401 Unauthorized)

**Audit Trail:**
- Log: timestamp, caller service, target service, endpoint, HTTP status
- Stored in `audit_logs` table
- Used for compliance and debugging

---

## 8. Resilience & Fault Tolerance

### 8.1 Circuit Breaker Pattern

<p align="center">
  <span style="background-color: white; padding: 20px; display: inline-block;">
    <img src="imgs/svg-07-circuit-breaker.svg" width="700" alt="Observability Stack"/>
  </span>
</p>


**Resilience4J Configuration (API Gateway):**

| Parameter | Value | Explanation |
|-----------|-------|-------------|
| **Sliding Window Size** | 10 calls | Track last 10 requests |
| **Failure Rate Threshold** | 50% | Open circuit if ≥50% fail |
| **Slow Call Threshold** | 60% | Open circuit if ≥60% slow |
| **Slow Call Duration** | 5 seconds | Define "slow" threshold |
| **Wait Duration (Open)** | 30 seconds | Wait before testing recovery |
| **Permitted Calls (Half-Open)** | 3 calls | Test calls in recovery |
| **Timeout** | 5 seconds | Request timeout |

**Circuit Breaker States:**

1. **CLOSED (Normal):**
    - All requests pass through to service
    - Failures and successes tracked
    - If failure rate > 50%, transition to OPEN

2. **OPEN (Failure Detected):**
    - Requests immediately fail without calling service
    - Fallback response returned
    - After 30 seconds, transition to HALF_OPEN

3. **HALF_OPEN (Testing Recovery):**
    - Allow 3 test requests
    - If all succeed → CLOSED (recovered)
    - If any fails → OPEN (still failing)

**Fallback Responses:**

| Service | Fallback Response |
|---------|------------------|
| User Service | `{ "error": "Authentication service temporarily unavailable" }` |
| Incident Service | `{ "error": "Incident service temporarily unavailable" }` |
| Notification Service | Silent failure (logged, not user-facing) |
| Reporting Service | `{ "error": "Reports temporarily unavailable, try cached version" }` |

### 8.2 Retry Pattern

**Retry Configuration:**
- **Max Attempts:** 3 total attempts (1 initial + 2 retries)
- **Backoff Strategy:** Exponential (1s, 2s, 4s)
- **Retryable Conditions:**
    - Network timeouts
    - Connection errors
    - 503 Service Unavailable
    - 429 Too Many Requests (rate limit)
- **Non-Retryable:**
    - 4xx client errors (400, 401, 403, 404)
    - Business validation errors

**Applied To:**
- Feign client calls (service-to-service REST)
- Kafka producer (broker connection failures)
- External API calls (email, SMS providers)

### 8.3 Bulkhead Pattern

**Purpose:** Isolate resources to prevent cascading failures

**Thread Pool Isolation:**
- Separate thread pool for each target service
- **Max Concurrent Calls:** 10 per service
- **Max Wait Time:** 500ms
- **Queue Capacity:** 20 requests

**Example Scenario:**
```
If Notification Service becomes slow:
- Only 10 concurrent requests allowed to Notification Service
- Additional requests wait max 500ms in queue
- After queue full, requests fail fast
- Other services (User, Incident) unaffected
- Prevents exhausting all Gateway threads
```

---

## 9. Observability Architecture

### 9.1 Three Pillars of Observability

<p align="center">
  <span style="background-color: white; padding: 20px; display: inline-block;">
    <img src="imgs/svg-08-observability-stack.svg" width="700" alt="Observability Stack"/>
  </span>
</p>


### 9.2 Metrics Collection (Prometheus + Grafana)

**Metrics Exposed by Each Service:**

| Metric Category | Examples | Purpose |
|----------------|----------|---------|
| **HTTP Metrics** | `http_requests_total`, `http_request_duration_seconds` | Request volume, latency (p50, p95, p99) |
| **Business Metrics** | `incidents_created_total`, `incidents_resolved_total`, `avg_resolution_time_seconds` | Business KPIs |
| **JVM Metrics** | `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live` | Resource usage |
| **Database Metrics** | `hikaricp_connections_active`, `hikaricp_connections_pending` | Connection pool health |
| **Kafka Metrics** | `kafka_consumer_lag`, `kafka_producer_record_send_total` | Message streaming health |
| **Circuit Breaker** | `resilience4j_circuitbreaker_state`, `resilience4j_circuitbreaker_failure_rate` | Resilience status |

**Prometheus Scraping:**
- Each service exposes `/actuator/prometheus` endpoint
- Prometheus scrapes every 15 seconds
- Time-series data stored for 30 days

**Grafana Dashboards:**
1. **Service Health:** Uptime, request rate, error rate per service
2. **Incident Analytics:** Creation rate, resolution time trends, status distribution
3. **Infrastructure:** CPU, memory, disk per container
4. **Business KPIs:** SLA compliance, priority distribution

### 9.3 Distributed Tracing (OpenTelemetry + Jaeger)


**Trace Propagation Mechanism:**
- **Trace ID:** Generated at API Gateway, propagated across all services
- **Span ID:** Each service operation creates a new span
- **Parent-Child Relationship:** Spans linked to show causality
- **Context Headers:** `X-Correlation-ID`, `traceparent` (W3C standard)

**Example Trace (Create Incident):**
```
Trace ID: abc-123-def-456-ghi-789
Total Duration: 350ms

├─ Span 1: API Gateway - Route Request [50ms]
│  └─ Operation: gateway.route
│  └─ Tags: http.method=POST, http.path=/api/v1/incidents
│
├─ Span 2: Incident Service - Create Incident [200ms]
│  ├─ Operation: incident.create
│  ├─ Tags: service=incident-service, userId=user-123
│  │
│  ├─ Span 3: PostgreSQL Insert [80ms]
│  │  └─ Operation: database.insert
│  │  └─ Tags: table=incidents
│  │
│  ├─ Span 4: Event Sourcing Insert [30ms]
│  │  └─ Operation: database.insert
│  │  └─ Tags: table=incident_events
│  │
│  └─ Span 5: Kafka Publish [40ms]
│     └─ Operation: kafka.send
│     └─ Tags: topic=incident.created
│
└─ Span 6: Notification Service - Process Event [100ms]
   ├─ Operation: kafka.consume
   ├─ Tags: topic=incident.created
   │
   └─ Span 7: Email Send [80ms]
      └─ Operation: http.post
      └─ Tags: api=email-provider
```

**Jaeger UI Capabilities:**
- View complete request flow across microservices
- Identify bottlenecks (slowest spans)
- Analyze error traces with full context
- Compare performance across time periods
- Search by trace ID, service, operation

### 9.4 Centralized Logging (ELK Stack)

**Log Flow:**
```
Microservices (JSON logs) → Logstash (collection + parsing) → Elasticsearch (indexing) → Kibana (visualization)
```

**Structured JSON Log Format:**
```json
{
  "@timestamp": "2026-01-09T15:30:45.123Z",
  "level": "INFO",
  "service": "incident-service",
  "correlationId": "abc-123-def-456",
  "traceId": "abc-123-def-456-ghi-789",
  "spanId": "span-002",
  "userId": "user-uuid-123",
  "action": "CREATE_INCIDENT",
  "incidentId": "incident-uuid-456",
  "message": "Incident created successfully",
  "duration": 200,
  "status": "success"
}
```

**Log Levels:**
- **ERROR:** Exceptions, failures requiring attention
- **WARN:** Degraded performance, retries, potential issues
- **INFO:** Business events (incident created, user logged in)
- **DEBUG:** Detailed flow for debugging (disabled in production)

**Kibana Dashboards:**
- Error rate by service and endpoint
- Top error messages with stack traces
- Request volume and response time trends
- User activity tracking (login, actions)
- Service-to-service call patterns

**Correlation ID Benefits:**
- Link all logs from a single user request
- Trace request across microservices
- Debug issues by searching correlation ID
- Generate complete request timeline

---

## 10. Deployment Architecture

### 10.1 Docker Compose Deployment


**Container Stack:**

| Container | Image | Ports | Volumes | Health Check |
|-----------|-------|-------|---------|--------------|
| discovery | openjdk:17-slim | 8761:8761 | - | curl localhost:8761/actuator/health |
| config | openjdk:17-slim | 8888:8888 | ./config-repo | curl localhost:8888/actuator/health |
| gateway | openjdk:17-slim | 8080:8080 | - | curl localhost:8080/actuator/health |
| user-service | openjdk:17-slim | 8081:8081 | - | curl localhost:8081/actuator/health |
| incident-service | openjdk:17-slim | 8082:8082 | - | curl localhost:8082/actuator/health |
| notification-service | openjdk:17-slim | 8083:8083 | - | curl localhost:8083/actuator/health |
| reporting-service | openjdk:17-slim | 8084:8084 | - | curl localhost:8084/actuator/health |
| ai-service | python:3.11-slim | 8085:8085 | - | curl localhost:8085/health |
| postgres | postgres:15 | 5432:5432 | postgres-data | pg_isready |
| mongodb | mongo:7 | 27017:27017 | mongo-data | mongosh --eval "db.adminCommand('ping')" |
| redis | redis:7-alpine | 6379:6379 | redis-data | redis-cli ping |
| zookeeper | confluentinc/cp-zookeeper | 2181:2181 | - | nc -z localhost 2181 |
| kafka | confluentinc/cp-kafka | 9092:9092 | - | kafka-topics --list |
| prometheus | prom/prometheus | 9090:9090 | ./prometheus.yml | curl localhost:9090/-/healthy |
| grafana | grafana/grafana | 3000:3000 | grafana-data | curl localhost:3000/api/health |

**Network Configuration:**
- All containers in `teletrack-network` bridge network
- Services discover each other by container name
- External access via localhost port mapping

**Environment Segregation:**
- `.env.dev`: Development (debug enabled, verbose logging)
- `.env.prod`: Production (optimized, minimal logging)

### 10.2 Service Discovery (Eureka)

<p align="center">
  <span style="background-color: white; padding: 20px; display: inline-block;">
    <img src="imgs/svg-09-service-discovery.svg" width="700" alt="Service Discovery"/>
  </span>
</p>


**Eureka Server:**
- Central registry at `http://discovery:8761/eureka`
- Provides service catalog with health status
- Self-preservation mode (retains registry during network issues)

**Service Registration:**
- Each service registers on startup:
    - Service name (e.g., "INCIDENT-SERVICE")
    - IP address and port
    - Health check endpoint (`/actuator/health`)
    - Metadata (version, environment)
- Heartbeat sent every 30 seconds
- If 3 consecutive heartbeats missed → marked as DOWN
- Deregisters on graceful shutdown

**Client-Side Load Balancing:**
- Gateway uses Spring Cloud LoadBalancer
- Round-robin strategy across healthy instances
- Local cache refreshed every 30 seconds
- Automatically excludes DOWN instances

**Benefits:**
- No hardcoded service URLs
- Dynamic scaling (add/remove instances)
- Automatic failover to healthy instances
- Health-based routing

### 10.3 Configuration Management (Spring Cloud Config)

**Configuration Repository Structure:**
```
config-repo/
├── application-dev.yml          # Dev environment overrides
├── application-prod.yml         # Prod environment overrides
├── user-service.yml             # User service specific
├── user-service-dev.yml         # User service dev overrides
├── incident-service.yml         # Incident service specific
├── notification-service.yml     # Notification service specific
├── reporting-service.yml        # Reporting service specific
└── api-gateway.yml              # Gateway specific
```

**Configuration Priority (highest to lowest):**
1. `{service-name}-{profile}.yml` (e.g., user-service-prod.yml)
2. `{service-name}.yml`
3. `application-{profile}.yml`
4. `application.yml`

**Configuration Fetch on Startup:**
```
Service starts → Connects to Config Service → 
Requests: /user-service/prod → 
Config Service reads: application.yml, application-prod.yml, user-service.yml, user-service-prod.yml →
Merges configs (with priority) → Returns to service
```

**Dynamic Configuration Refresh:**
- POST to `/actuator/refresh` endpoint
- Only refreshes `@RefreshScope` beans
- No service restart required
- Use case: Change log level, feature flags

**Benefits:**
- Centralized configuration management
- Environment-specific configs
- No rebuild needed for config changes
- Version controlled configs (Git)

---

## 11. API Design & Documentation

### 11.1 RESTful API Design

**URL Structure:**
- Base URL: `http://localhost:8080/api/v1` (via Gateway)
- Version in path: `/api/v1/{resource}`
- Resource naming: Plural nouns (`/incidents`, `/users`, `/reports`)
- Hierarchy: `/incidents/{id}/comments` (nested resources)

**HTTP Methods & Semantics:**
- **GET:** Retrieve resource(s) - Idempotent, no side effects
- **POST:** Create new resource - Non-idempotent
- **PUT:** Full update (replace entire resource)
- **PATCH:** Partial update (modify specific fields)
- **DELETE:** Remove resource

**Status Codes:**

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (include Location header) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation errors, malformed JSON |
| 401 | Unauthorized | Missing or invalid JWT token |
| 403 | Forbidden | Valid token but insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Business rule violation (duplicate) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unhandled exception |
| 503 | Service Unavailable | Circuit breaker open, service down |

### 11.2 Response Format Standardization

**Success Response:**
```json
{
  "status": "success",
  "data": {
    "id": "uuid-123",
    "title": "Network outage"
  },
  "timestamp": "2026-01-09T15:30:45.123Z",
  "correlationId": "abc-123-def"
}
```

**Error Response:**
```json
{
  "status": "error",
  "message": "Incident not found",
  "errorCode": "INC_404",
  "timestamp": "2026-01-09T15:30:45.123Z",
  "correlationId": "abc-123-def"
}
```

**Paginated Response:**
```json
{
  "status": "success",
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalPages": 5,
    "totalElements": 98,
    "first": true,
    "last": false
  },
  "timestamp": "2026-01-09T15:30:45.123Z"
}
```

### 11.3 API Documentation (Swagger/OpenAPI 3)

**Swagger UI Locations:**
- Aggregated (via Gateway): `http://localhost:8080/swagger-ui.html`
- User Service: `http://localhost:8081/swagger-ui.html`
- Incident Service: `http://localhost:8082/swagger-ui.html`

**Documentation Includes:**
- Endpoint paths and HTTP methods
- Request parameters (path, query, body)
- Request/response schemas with examples
- Authentication requirements (Bearer token)
- Response codes with descriptions
- Business error codes

---

## 12. Non-Functional Requirements

| Category | Requirement | Target | Implementation |
|----------|------------|--------|----------------|
| **Availability** | System uptime | 99.5% | Circuit breakers, health checks, automatic restarts |
| **Scalability** | Concurrent users | 1000+ | Horizontal scaling, load balancing, caching |
| **Performance** | Response time (p95) | <500ms | Database indexing, Redis caching, async processing |
| **Throughput** | Requests/second | 100/service | Non-blocking I/O, Kafka for async work |
| **Reliability** | Data consistency | ACID for critical data | PostgreSQL transactions, event sourcing |
| **Security** | Unauthorized access | Zero tolerance | OAuth2/JWT, RBAC, API keys, audit logging |
| **Observability** | Request tracing | 100% coverage | OpenTelemetry, correlation IDs, structured logs |
| **Maintainability** | Service isolation | Independent deployment | Microservices, externalized config |
| **Recoverability** | Recovery time | <5 minutes | Circuit breaker recovery, automated health checks |

---

## 13. Technology Stack Summary

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Language** | Java | 17+ | Backend services |
| **Framework** | Spring Boot | 3.x | Microservices framework |
| **Service Discovery** | Netflix Eureka | Latest | Service registry |
| **API Gateway** | Spring Cloud Gateway | Latest | Routing, security, resilience |
| **Configuration** | Spring Cloud Config | Latest | Centralized config |
| **HTTP Client** | OpenFeign | Latest | Declarative REST client |
| **Resilience** | Resilience4J | Latest | Circuit breaker, retry, bulkhead |
| **Messaging** | Apache Kafka | 3.x | Event streaming |
| **DB (Relational)** | PostgreSQL | 15 | Transactional data |
| **DB (Document)** | MongoDB | 7 | Analytics data |
| **Cache** | Redis | 7 | High-speed caching |
| **ORM** | Spring Data JPA | Latest | Database abstraction |
| **Migration** | Liquibase | Latest | Schema versioning |
| **Security** | Spring Security + JWT | Latest | Authentication/authorization |
| **Monitoring** | Prometheus + Grafana | Latest | Metrics and dashboards |
| **Tracing** | OpenTelemetry + Jaeger | Latest | Distributed tracing |
| **Logging** | Logback + ELK Stack | Latest | Centralized logging |
| **Documentation** | SpringDoc OpenAPI | Latest | Swagger UI generation |
| **Build** | Maven | 3.9+ | Dependency management |
| **Container** | Docker + Compose | Latest | Deployment |
| **CI/CD** | GitHub Actions | Latest | Automated pipeline |
| **AI Module** | Flask (Python) | 3.11 | Pattern analysis |

---

## 14. Key Design Decisions & Trade-offs

### 14.1 Microservices vs Monolith
**Decision:** Microservices architecture  
**Rationale:** Independent scaling, fault isolation, team autonomy, technology flexibility  
**Trade-off:** Increased operational complexity, distributed system challenges, network overhead

### 14.2 Event-Driven Communication
**Decision:** Kafka for asynchronous messaging  
**Rationale:** Service decoupling, handles high throughput, enables event sourcing, pub-sub pattern  
**Trade-off:** Eventual consistency, debugging complexity, requires Kafka infrastructure

### 14.3 Polyglot Persistence
**Decision:** PostgreSQL + MongoDB + Redis  
**Rationale:** Use optimal database for each use case (ACID, analytics, caching)  
**Trade-off:** Multiple database technologies to learn/maintain, cross-database transactions impossible

### 14.4 API Gateway Pattern
**Decision:** Single entry point via Spring Cloud Gateway  
**Rationale:** Centralized security, routing, rate limiting, circuit breaking  
**Trade-off:** Single point of failure (mitigated by health checks and failover), slight latency

### 14.5 Service-to-Service Security
**Decision:** API Key authentication (not OAuth2 Client Credentials)  
**Rationale:** Simpler implementation for MVP, sufficient for trusted internal services  
**Trade-off:** Less fine-grained than OAuth2 scopes, manual key rotation

### 14.6 Synchronous REST (Feign) + Async Kafka
**Decision:** Hybrid communication  
**Rationale:** REST for immediate responses, Kafka for fire-and-forget operations  
**Trade-off:** Two communication patterns to manage

---

## 15. Future Enhancements (Post-MVP)

1. **Kubernetes Orchestration:** Migrate from Docker Compose to K8s for production
2. **HashiCorp Vault:** Externalized secrets management with automatic rotation
3. **Service Mesh (Istio/Linkerd):** mTLS, advanced traffic management, observability
4. **GraphQL Gateway:** Flexible query interface for frontend clients
5. **Event Replay Utility:** Admin tool to replay Kafka events for debugging
6. **Chaos Engineering:** Automated failure injection (Netflix Chaos Monkey style)
7. **Multi-Region Deployment:** Active-active DR with database geo-replication
8. **Advanced AI:** Real-time anomaly detection, predictive incident routing
9. **Blue-Green Deployment:** Zero-downtime deployments
10. **API Rate Limiting per User:** More granular rate limits based on user tier

---

## 16. Success Criteria

- All 8 microservices deployed and communicating  
- Service discovery working (Eureka)  
- Centralized configuration working (Config Service)  
- User authentication with JWT functional  
- RBAC enforced (ADMIN, OPERATOR, SUPPORT)  
- Incident CRUD operations working  
- Event sourcing recording all incident changes  
- Kafka events flowing between services  
- Notifications triggered on incident creation/updates  
- Reports generated from MongoDB with Redis caching  
- Circuit breaker fallbacks working  
- Distributed tracing showing complete flows  
- Prometheus metrics collected from all services  
- Grafana dashboards operational  
- ELK stack collecting and displaying logs  
- API documentation accessible via Swagger  
- All services containerized via Docker Compose  
- Service-to-service security implemented (API keys)  
- Feign clients working for synchronous calls
