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
| API Gateway      | Spring Cloud Gateway 5.0.2 (WebFlux)         |
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
├── api-gateway/            # ✅ implemented (Phase 2)
├── user-service/           # scaffold only (Phase 3)
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
| `api-gateway`     | ✅ Done — Spring Cloud Gateway, port 8080   |
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

This compiles every implemented module and runs its tests (Eureka context-load,
Config Server endpoint and API Gateway routing tests).

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

## Running the API Gateway

Build first (see above), start the Eureka Server and Config Server (previous
sections), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl api-gateway spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.gateway.ApiGatewayApplication
```

On a successful start you will see:

```
Netty started on port 8080 (http)
Started ApiGatewayApplication ...
```

The gateway pulls its configuration (including the route table) from the Config
Server, registers with Eureka as `API-GATEWAY` and fetches the registry to
resolve backend services. If the Config Server is unavailable it still starts
(with a warning) but without routes.

### Route table

| Route (request path) | Target (Eureka service ID) |
|----------------------|----------------------------|
| `/api/users/**`      | `lb://USER-SERVICE`        |
| `/api/vehicles/**`   | `lb://VEHICLE-SERVICE`     |
| `/api/parking/**`    | `lb://PARKING-SERVICE`     |
| `/api/payments/**`   | `lb://PAYMENT-SERVICE`     |

The `lb://` scheme tells the gateway to resolve the service name through
**Eureka service discovery** and load-balance across its registered instances —
no backend host/port is hard-coded. Routes are defined centrally in
`config-server/src/main/resources/config/api-gateway.yml`.

### Verifying

| Check                        | URL / Command                                                    |
|------------------------------|------------------------------------------------------------------|
| Gateway health (actuator)    | http://localhost:8080/actuator/health                            |
| Active routes (actuator)     | http://localhost:8080/actuator/gateway/routes                    |
| Eureka registry (API-GATEWAY)| http://localhost:8761/eureka/apps                                |

### Example requests

Until the backend services are implemented (Phase 3), a proxied request reaches
the gateway route and then fails with `503` because no service instance is
registered yet — this proves discovery-based routing is wired up. Full details
are in `api-gateway/README.md`.

```bash
# User Service (Phase 3 target: USER-SERVICE) - currently 503, no instance yet
curl -i http://localhost:8080/api/users

# Vehicle Service (Phase 3 target: VEHICLE-SERVICE)
curl -i http://localhost:8080/api/vehicles/1

# Parking Service (Phase 3 target: PARKING-SERVICE)
curl -i http://localhost:8080/api/parking/spaces

# Payment Service (Phase 3 target: PAYMENT-SERVICE)
curl -i http://localhost:8080/api/payments/1

# Unknown path - routed nowhere, gateway returns 404
curl -i http://localhost:8080/nope
```

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
