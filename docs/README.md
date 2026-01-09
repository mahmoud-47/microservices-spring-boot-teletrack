## Project Overview
Microservices-based incident management system with:
- 4 Business Services (User, Incident, Notification, Reporting)
- 3 Infrastructure Services (Discovery, Config, API Gateway)
- PostgreSQL Database
- Kafka Message Broker

## Architecture

### Services
1. **Discovery Service** (Eureka) - Port 8761
2. **Config Service** - Port 8888
3. **API Gateway** - Port 8080
4. **User Service** - Port 8081
5. **Incident Service** - Port 8082
6. **Notification Service** - Port 8083
7. **Reporting Service** - Port 8084

### Infrastructure
- PostgreSQL 15 - Port 5432
- Kafka + Zookeeper - Port 9092