# TeleTrack360 - Incident Management System

Modern microservices platform for telecommunications incident management with real-time notifications, analytics, and AI-powered insights.

## Architecture

**8 Microservices:**
- Discovery Service (Eureka) - Service registry
- Config Service - Centralized configuration
- API Gateway - Routing, security, circuit breaker
- User Service - Authentication & authorization
- Incident Service - Core domain logic
- Notification Service - Multi-channel alerts
- Reporting Service - Analytics & AI integration
- AI Service - Pattern detection (Python)

**Design Patterns:** Event Sourcing, CQRS, Circuit Breaker, Service Discovery, API Gateway

## Tech Stack

- **Java 21** | Spring Boot 3.5.9 | Spring Cloud 2025.0.1
- **Data:** PostgreSQL 42.7.1, MongoDB, Redis, Kafka
- **Security:** JWT (jjwt 0.12.3), OAuth2
- **Resilience:** Resilience4J 2.2.0
- **Observability:** Prometheus, Grafana, Jaeger, ELK
- **Tools:** Lombok 1.18.36, MapStruct 1.5.5, Liquibase 4.25.0

## Quick Start

```bash
# Prerequisites: Java 21, Docker, Maven 3.8+

# Start infrastructure
docker-compose up -d

# Build & run
mvn clean install
cd user-service && mvn spring-boot:run
```

**Access Points:**
- API Gateway: http://localhost:8080
- Eureka: http://localhost:8761
- Grafana: http://localhost:3000

## Key Features

- **Polyglot Persistence:** PostgreSQL (transactional), MongoDB (analytics), Redis (cache)
- **Event-Driven:** Kafka with event sourcing for complete audit trail
- **Security:** OAuth2/JWT with 15-min access tokens, 7-day refresh tokens
- **Resilience:** Circuit breaker, retry, bulkhead patterns
- **Observability:** Distributed tracing, metrics, centralized logging

## API Endpoints

```
POST   /api/v1/auth/login          # Authentication
POST   /api/v1/auth/register       # User registration
GET    /api/v1/incidents           # List incidents
POST   /api/v1/incidents           # Create incident
PATCH  /api/v1/incidents/{id}      # Update incident
GET    /api/v1/reports/daily       # Daily reports
```

## Testing

```bash
mvn test                           # Unit tests (80% coverage target)
mvn verify -P integration-tests    # Integration tests with Testcontainers
mvn jacoco:report                  # Coverage report
```

## Documentation

- [HLD.md](docs/HLD.md) - High-Level Design
- [LLD.md](docs/LLD.md) - Low-Level Design
- [diagrams/](docs/diagrams/) - Architecture diagrams

## Project Structure

```
teletrack360/
├── shared/common-utils/           # Shared DTOs, exceptions, events
├── discovery/discovery-service/   # Eureka
├── config/config-service/         # Config Server
├── gateway/api-gateway/           # Gateway
└── services/
    ├── user-service/
    ├── incident-service/
    ├── notification-service/
    └── reporting-service/
```

---

**Assessment Project** | Java Backend Engineering | Advanced Level 2