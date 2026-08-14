# Smart Parking Management System (SPMS)

Backend-only, microservice-based application that manages parking operations end to end:
users, their vehicles, parking spaces, parking sessions and payments.

## Tech Stack

| Concern          | Technology                                  |
|------------------|---------------------------------------------|
| Language         | Java 17 (built with JDK 17+, verified on 21) |
| Framework        | Spring Boot 4.1.0                            |
| Microservices    | Spring Cloud 2025.1.2 (Oakwood)              |
| Service Registry | Spring Cloud Netflix Eureka Server 5.0.2     |
| Config Server    | Spring Cloud Config Server 5.0.4             |
| Build tool       | Apache Maven (Maven Wrapper `mvnw` included) |
| API style        | REST (JSON)                                  |

> Spring Cloud 2025.1.2 is required — earlier trains (e.g. 2025.1.1) reject Spring
> Boot 4.1.0 via the Spring Cloud compatibility verifier.

## Purpose

SPMS lets parking operators and drivers manage parking through REST APIs:

- Register and manage **users** (drivers, operators, admins)
- Register and manage **vehicles** owned by users
- Track **parking spaces** and their live availability
- Book and run **parking sessions**
- Process **payments** for parking sessions

The system is deliberately backend-only: no frontend or UI. All interaction is
through REST endpoints exposed via an API gateway.

## Microservice Architecture

```
                        ┌──────────────────┐
                        │   api-gateway     │  (Spring Cloud Gateway, single entry point)
                        └────────┬─────────┘
                                 │ routes by service name (load-balanced via Eureka)
        ┌───────────────┬────────┴─────────┬───────────────┬───────────────┐
        │               │                  │               │               │
┌───────▼──────┐ ┌──────▼───────┐ ┌────────▼───────┐ ┌─────▼────────┐ ┌─────▼────────┐
│ user-service │ │vehicle-service│ │parking-service│ │payment-service│ │   ...        │
└───────┬──────┘ └──────┬───────┘ └────────┬───────┘ └─────┬────────┘ └──────────────┘
        │               │                  │               │
        └───────────────┴────────┬─────────┴───────────────┘
                                 │
                       ┌─────────▼─────────┐        ┌───────────────────┐
                       │   eureka-server   │◄──────►│   config-server    │
                       │  (port 8761)      │        │ (centralized config)
                       └───────────────────┘        └───────────────────┘
```

| Module           | Port (planned) | Role                                                                  |
|------------------|---------------|-----------------------------------------------------------------------|
| `eureka-server`  | 8761          | Netflix Eureka Service Registry (discovery server)                   |
| `config-server`  | 8888          | Spring Cloud Config Server (centralized external configuration)      |
| `api-gateway`    | 8080          | Spring Cloud Gateway (single REST entry point, routing + load balancer)|
| `user-service`   | 8081          | Users, authentication and profiles                                    |
| `vehicle-service`| 8082          | Vehicle registration and management                                   |
| `parking-service`| 8083          | Parking spaces, availability, sessions                                |
| `payment-service`| 8084          | Payments and billing                                                  |

### Project layout

```
smart-parking-management-system/
├── pom.xml                 # Multi-module aggregator / parent POM
├── eureka-server/          # ✅ implemented (Phase 1)
├── config-server/          # ✅ implemented (Phase 2)
├── api-gateway/            # scaffold only (Phase 2+)
├── user-service/           # scaffold only (Phase 2+)
├── vehicle-service/        # scaffold only (Phase 2+)
├── parking-service/        # scaffold only (Phase 2+)
├── payment-service/        # scaffold only (Phase 2+)
├── docs/                   # architecture / API documentation
├── postman_collection.json # Postman collection for the REST APIs
├── mvnw / mvnw.cmd         # Maven Wrapper (Maven 3.9.16)
└── README.md
```

## Current Implementation Status

| Component         | Status                                       |
|-------------------|----------------------------------------------|
| Multi-module POM  | ✅ Done                                      |
| `eureka-server`   | ✅ Done — standalone Eureka registry, port 8761 |
| `config-server`   | ✅ Done — native-repo Config Server, port 8888 |
| `api-gateway`     | ⏳ Pending (Phase 2)                         |
| `user-service`    | ⏳ Pending (Phase 3)                         |
| `vehicle-service` | ⏳ Pending (Phase 3)                         |
| `parking-service` | ⏳ Pending (Phase 3)                         |
| `payment-service` | ⏳ Pending (Phase 3)                         |

## Building

Requires a JDK 17+ (Java 21 works). Maven itself is not required — the Maven
Wrapper downloads Maven 3.9.16 on first use.

```bash
# Windows
mvnw.cmd clean install

# Linux / macOS
./mvnw clean install
```

This compiles every implemented module and runs its tests (currently the
Eureka Server context-load test).

## Running the Eureka Server

Build first (see above), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl eureka-server spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.eureka.EurekaServerApplication
```

On a successful start you will see:

```
Tomcat started on port 8761 (http) with context path '/'
Started EurekaServerApplication ...
```

### Verifying

| Check                     | URL / Command                                                        |
|---------------------------|----------------------------------------------------------------------|
| Eureka dashboard          | http://localhost:8761/                                                |
| Health (actuator)         | http://localhost:8761/actuator/health                                 |
| Registered applications   | http://localhost:8761/eureka/apps                                     |

The dashboard shows **"Instances currently registered with Eureka"** — after
starting the Config Server this will list `CONFIG-SERVER`. The server itself is
intentionally standalone: it neither registers itself
(`eureka.client.register-with-eureka=false`) nor fetches the registry from a
peer (`eureka.client.fetch-registry=false`).

## Running the Config Server

Build first (see above), start the Eureka Server (see previous section), then
run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl config-server spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.config.ConfigServerApplication
```

On a successful start you will see:

```
Tomcat started on port 8888 (http) with context path '/'
Started ConfigServerApplication ...
```

### Verifying

| Check                           | URL / Command                                              |
|---------------------------------|------------------------------------------------------------|
| Config Server health (actuator) | http://localhost:8888/actuator/health                      |
| Config for api-gateway          | http://localhost:8888/api-gateway/default                  |
| Config for user-service         | http://localhost:8888/user-service/default                 |
| Config for vehicle-service      | http://localhost:8888/vehicle-service/default              |
| Config for parking-service      | http://localhost:8888/parking-service/default              |
| Config for payment-service      | http://localhost:8888/payment-service/default              |
| Shared config (all services)    | http://localhost:8888/application/default                  |
| Eureka registry (CONFIG-SERVER) | http://localhost:8761/eureka/apps                          |

The Config Server uses a **native repository** (`classpath:/config`) served on
port 8888 and registers with Eureka as `CONFIG-SERVER`. The default `Accept`
header returns YAML; `Accept: application/json` returns JSON. Each response
contains the service-specific property source plus the shared `application.yml`
source. Full details are in `config-server/README.md`.

## Configuration

All Eureka configuration lives in `eureka-server/src/main/resources/application.properties`.
Key settings:

```properties
server.port=8761
spring.application.name=eureka-server
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
eureka.instance.hostname=localhost
eureka.server.enable-self-preservation=false
```
