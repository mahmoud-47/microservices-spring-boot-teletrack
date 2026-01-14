# Dev Reflection: TeleTrack360 Implementation

## Project-Based Assessment

### How did you approach modularization?
I followed a **Domain-Driven Design (DDD)** approach to define bounded contexts. Each microservice (User, Incident, Reporting, etc.) represents a distinct business capability.
* **Maven Multi-Module:** I used a parent-child structure to manage dependencies centrally.
* **Shared Library:** A `common-utils` module was created to house shared DTOs, Kafka events, and custom exceptions, ensuring type safety across the distributed system while keeping business logic isolated.

### What were the trade-offs you encountered?
* **Consistency vs. Availability:** I chose **Eventual Consistency** for the Reporting Service. Using Kafka to sync PostgreSQL data to MongoDB allowed for high availability and fast reads, but meant that reports might be a few milliseconds behind the actual transaction.
* **Microservices Overhead:** While modularity is great, it increased network latency and complexity (e.g., managing 8 different ports and configurations). I mitigated this using **Spring Cloud Config** and **Eureka** to automate the "plumbing."

### How did you implement service-to-service security?
I implemented a **Defense-in-Depth** strategy:
1. **Edge Security:** The API Gateway validates JWTs and manages OAuth2 flows.
2. **Internal Trust:** I used **Service-to-Service API Key Authentication**. The Gateway injects a secret `X-Service-Key` into internal requests. Each downstream service uses a security filter to validate this key, preventing unauthorized internal access even if the network is breached.

### What failed and how did you debug it?
* **The Failure:** During CI/CD setup, the build failed due to **SpotBugs** "Internal Representation" errors and **Maven Parent POM** resolution issues.
* **The Debugging:** I analyzed the GitHub Actions logs, identified that `relativePath` was missing in child POMs, and realized that SpotBugs was being too pedantic for DTOs.
* **The Fix:** I updated the parent POM to include a `spotbugs-exclude.xml` filter and corrected the directory paths, restoring the "Green" build status.

### Which part would you improve if you had more time?
I would implement the **Saga Pattern** for the incident lifecycle. Currently, if an incident is saved but the Kafka notification fails, the system lacks a built-in "rollback." A State-Machine-based Saga would ensure 100% data integrity across the Incident and Notification services.

---

## Mock Interview Questions

### What design patterns did you apply and why?
* **CQRS (Command Query Responsibility Segregation):** PostgreSQL handles commands (writes), while MongoDB handles queries (reporting). This prevents heavy analytics from slowing down user operations.
* **Circuit Breaker:** Applied via **Resilience4J** to stop cascading failures if the AI or Notification services go offline.
* **Token Bucket:** Used in the Gateway for **Rate Limiting** to protect against API DOS attacks.

### How would you optimize incident report queries?
I would implement **Compound Indexing** in MongoDB on frequently filtered fields like `status` and `priority`. I would also use **Aggregation Pipelines** with `$match` and `$group` stages to perform calculations on the database side rather than in Java memory, significantly reducing latency for large datasets.

### How does your system handle spikes in incident creation?
The system uses **Kafka as a buffer**. During a spike, the `incident-service` produces events to a topic. The `notification-service` and `reporting-service` consume these at their own pace. If a backlog grows, I can horizontally scale the consumer instances to increase throughput without putting pressure on the primary Incident database.



### How would you integrate caching in reporting?
I have integrated **Redis** using the **Cache-Aside** pattern. For expensive operations like `getTrendAnalysis`, the service first checks Redis. If data is missing (Cache Miss), it queries MongoDB and populates the cache with a 15-minute TTL. This reduces the load on MongoDB for frequently viewed dashboards.

### What would change if PostgreSQL was replaced with MongoDB?
* **Pros:** We would gain high write scalability and a flexible schema for evolving incident types.
* **Cons:** We would lose **ACID transactions** across multiple collections and the ability to perform complex SQL joins. We would have to use "Document Embedding" to avoid the lack of joins, potentially leading to larger document sizes.

### How would you enable blue-green deployment for your services?
I would use a **Kubernetes Service** or an **NGINX** load balancer. I would deploy the new version (Green) alongside the current one (Blue). Once the Green version passes health checks, I would flip the load balancer's selector to point to Green. If issues arise, I can instantly flip the traffic back to the Blue environment.