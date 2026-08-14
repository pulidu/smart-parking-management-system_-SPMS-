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
| User Service     | Spring Boot 4.1.0 WebMVC + Spring Data JPA + PostgreSQL + Bean Validation |
| Vehicle Service  | Spring Boot 4.1.0 WebMVC + Spring Data JPA + PostgreSQL + Bean Validation |
| Parking Service  | Spring Boot 4.1.0 WebMVC + Spring Data JPA + PostgreSQL + Bean Validation |
| Payment Service  | Spring Boot 4.1.0 WebMVC + Spring Data JPA + PostgreSQL + Bean Validation (mock payment gateway) |
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
├── user-service/           # ✅ implemented (Phase 3)
├── vehicle-service/        # ✅ implemented (Phase 3)
├── parking-service/        # ✅ implemented (Phase 4)
├── payment-service/        # ✅ implemented (Phase 5)
├── docs/                   # architecture / API / database documentation
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
| `user-service`    | ✅ Done — users, auth, profiles, port 8081  |
| `vehicle-service` | ✅ Done — vehicles + entry/exit, port 8082 |
| `parking-service` | ✅ Done — spaces, search, reservations, port 8083 |
| `payment-service` | ✅ Done — mock payments, receipts, port 8084 |

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
Config Server endpoint, API Gateway routing, User Service and Vehicle Service
integration tests).

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

All backend services are implemented, so `/api/users`, `/api/vehicles`,
`/api/parking` and `/api/payments` requests are routed end-to-end. Full details
are in `api-gateway/README.md`.

```bash
# User Service (implemented - routes to USER-SERVICE)
curl -i http://localhost:8080/api/users

# Vehicle Service (implemented - routes to VEHICLE-SERVICE)
curl -i http://localhost:8080/api/vehicles/1

# Parking Service (implemented - routes to PARKING-SERVICE)
curl -i http://localhost:8080/api/parking/spaces

# Payment Service (implemented - routes to PAYMENT-SERVICE)
curl -i http://localhost:8080/api/payments/1

# Unknown path - routed nowhere, gateway returns 404
curl -i http://localhost:8080/nope
```

## Running the User Service

Build first (see above), start the Eureka Server, Config Server and API Gateway
(previous sections), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl user-service spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.user.UserServiceApplication
```

The service reads its PostgreSQL connection from environment variables
(`DB_HOST`, `DB_PORT`, `USER_DB_NAME`, `USER_DB_USERNAME`, `USER_DB_PASSWORD`)
and registers with Eureka as `USER-SERVICE` on port `8081`. See
`docs/database-setup.md` for the PostgreSQL provisioning steps.

On a successful start you will see:

```
Tomcat started on port 8081 (http) with context path '/'
Started UserServiceApplication ...
```

### Endpoints

| Method | Path                     | Description                     |
|--------|--------------------------|---------------------------------|
| POST   | `/api/users`             | Register a user                 |
| POST   | `/api/users/login`       | Authenticate (email + password) |
| GET    | `/api/users/{id}`        | View a user profile             |
| PUT    | `/api/users/{id}`        | Update a user profile           |
| GET    | `/api/users/{id}/bookings` | Booking history (placeholder) |

Roles: `DRIVER`, `OWNER`, `ADMIN`. Errors: 400 invalid input, 401 invalid login,
404 user not found, 409 duplicate email. Full request/response samples are in
`user-service/README.md`.

### Verifying

| Check                          | URL / Command                                        |
|--------------------------------|------------------------------------------------------|
| User Service health (actuator) | http://localhost:8081/actuator/health                |
| Register a user (direct)       | `curl -X POST http://localhost:8081/api/users -H "Content-Type: application/json" -d "{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\"secret123\",\"role\":\"DRIVER\"}"` |
| Register via API Gateway       | Same POST but on `http://localhost:8080/api/users`   |
| Eureka registry (USER-SERVICE) | http://localhost:8761/eureka/apps                    |

### Example request/response

```bash
curl -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Driver","email":"alice@example.com","password":"secret123","phone":"+1-555-0100","role":"DRIVER"}'
```

```json
{
  "id": 1,
  "name": "Alice Driver",
  "email": "alice@example.com",
  "phone": "+1-555-0100",
  "role": "DRIVER",
  "createdAt": "2026-08-14T14:12:37.111561Z",
  "updatedAt": "2026-08-14T14:12:37.111561Z"
}
```

> Tip: the response of `POST /api/users` is the profile; capture `id` from it for
> the `{id}`-based endpoints. Passwords are stored as BCrypt hashes and never
> returned. `POST /api/users/login` returns `{"token": null, "user": {...}}` —
> `token` is reserved for a future JWT/security layer.

## Running the Vehicle Service

Build first (see above), start the Eureka Server, Config Server and API Gateway
(previous sections), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl vehicle-service spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar vehicle-service/target/vehicle-service-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.vehicle.VehicleServiceApplication
```

The service reads its PostgreSQL connection from environment variables
(`DB_HOST`, `DB_PORT`, `VEHICLE_DB_NAME`, `VEHICLE_DB_USERNAME`,
`VEHICLE_DB_PASSWORD`) and registers with Eureka as `VEHICLE-SERVICE` on port
`8082`. See `docs/database-setup.md` for the PostgreSQL provisioning steps.

On a successful start you will see:

```
Tomcat started on port 8082 (http) with context path '/'
Started VehicleServiceApplication ...
```

### Endpoints

| Method | Path                          | Description                     |
|--------|-------------------------------|---------------------------------|
| POST   | `/api/vehicles`               | Register a vehicle              |
| GET    | `/api/vehicles/{id}`          | Get a vehicle                   |
| GET    | `/api/vehicles/user/{userId}` | List a user's vehicles          |
| PUT    | `/api/vehicles/{id}`          | Update a vehicle                |
| DELETE | `/api/vehicles/{id}`          | Delete a vehicle (204)          |
| POST   | `/api/vehicles/{id}/entry`    | Simulate entry (→ `INSIDE`)     |
| POST   | `/api/vehicles/{id}/exit`     | Simulate exit (→ `OUTSIDE`)     |

Vehicle types: `CAR`, `SUV`, `VAN`, `TRUCK`, `MOTORCYCLE`, `BUS`. Status:
`OUTSIDE` / `INSIDE`. Errors: 400 invalid input, 404 not found, 409 duplicate
number or invalid entry/exit transition. Full request/response samples are in
`vehicle-service/README.md`.

### Verifying

| Check                            | URL / Command                                        |
|----------------------------------|------------------------------------------------------|
| Vehicle Service health (actuator) | http://localhost:8082/actuator/health               |
| Register a vehicle (direct)      | `curl -X POST http://localhost:8082/api/vehicles -H "Content-Type: application/json" -d "{\"userId\":1,\"vehicleNumber\":\"ABC-1234\",\"vehicleType\":\"CAR\",\"brand\":\"Toyota\",\"model\":\"Corolla\"}"` |
| Register via API Gateway         | Same POST but on `http://localhost:8080/api/vehicles` |
| Eureka registry (VEHICLE-SERVICE)| http://localhost:8761/eureka/apps                    |

### Example entry/exit flow

```bash
curl -X POST http://localhost:8082/api/vehicles/1/entry   # → "status": "INSIDE", entryTime set
curl -X POST http://localhost:8082/api/vehicles/1/exit    # → "status": "OUTSIDE", exitTime set
curl -X POST http://localhost:8082/api/vehicles/1/exit    # → 409 (already outside)
```

The status is a state machine: entry on an `INSIDE` vehicle and exit on an
`OUTSIDE` vehicle are rejected with `409 Conflict`; entry/exit on an unknown id
returns `404`.

## Running the Parking Service

Build first (see above), start the Eureka Server, Config Server and API Gateway
(previous sections), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl parking-service spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar parking-service/target/parking-service-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.parking.ParkingServiceApplication
```

The service reads its PostgreSQL connection from environment variables
(`DB_HOST`, `DB_PORT`, `PARKING_DB_NAME`, `PARKING_DB_USERNAME`,
`PARKING_DB_PASSWORD`) and registers with Eureka as `PARKING-SERVICE` on port
`8083`. See `docs/database-setup.md` for the PostgreSQL provisioning steps.

On a successful start you will see:

```
Tomcat started on port 8083 (http) with context path '/'
Started ParkingServiceApplication ...
```

### Endpoints

| Method | Path                                     | Description                    |
|--------|------------------------------------------|--------------------------------|
| POST   | `/api/parking/spaces`                    | Create a parking space         |
| GET    | `/api/parking/spaces`                    | Search/filter spaces           |
| GET    | `/api/parking/spaces/{id}`               | Get a space                    |
| PUT    | `/api/parking/spaces/{id}`               | Update a space                 |
| DELETE | `/api/parking/spaces/{id}`               | Delete a space (204)           |
| PUT    | `/api/parking/spaces/{id}/status`        | Manual/IoT status update       |
| POST   | `/api/parking/reservations`              | Reserve a space                |
| GET    | `/api/parking/reservations/{id}`         | Get a reservation              |
| GET    | `/api/parking/reservations/user/{userId}`| List a user's reservations     |
| POST   | `/api/parking/reservations/{id}/cancel`  | Cancel a reservation           |
| POST   | `/api/parking/reservations/{id}/release` | Release a reservation          |

Space status: `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`. Reservation
status: `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`. Search supports
`?city=`, `?zone=` and `?available=true|false`. Errors: 400 invalid input,
404 not found, 409 duplicate/state conflict. Full request/response samples are
in `parking-service/README.md`.

### Verifying

| Check                            | URL / Command                                        |
|----------------------------------|------------------------------------------------------|
| Parking Service health (actuator) | http://localhost:8083/actuator/health               |
| Create a space (direct)          | `curl -X POST http://localhost:8083/api/parking/spaces -H "Content-Type: application/json" -d "{\"ownerId\":1,\"spaceNumber\":\"A-01\",\"location\":\"Level 1\",\"city\":\"Colombo\",\"zone\":\"Zone-A\",\"pricePerHour\":5.50}"` |
| Create via API Gateway           | Same POST but on `http://localhost:8080/api/parking/spaces` |
| Search available spaces          | `curl "http://localhost:8083/api/parking/spaces?city=Colombo&available=true"` |
| Eureka registry (PARKING-SERVICE)| http://localhost:8761/eureka/apps                    |

### Example reservation flow

```bash
curl -X POST http://localhost:8083/api/parking/reservations \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"vehicleId":1,"parkingSpaceId":1,"startTime":"2026-08-20T10:00:00Z","endTime":"2026-08-20T12:00:00Z"}'
# → 201, reservation "PENDING", space "AVAILABLE" → "RESERVED"

curl -X POST http://localhost:8083/api/parking/reservations/1/release
# → 200, reservation "COMPLETED", space back to "AVAILABLE"

curl -X POST http://localhost:8083/api/parking/reservations/1/cancel
# → 409 (no longer active)
```

Reserving requires the space to exist (404) and be `AVAILABLE` (409), a
`userId`/`vehicleId`/`startTime`/`endTime`, and `startTime` before `endTime`
(400). A space with an active reservation cannot be double-booked (409) — the
reservation is transactional and the space row is pessimistically locked, so
concurrent attempts serialize and only one succeeds.

## Running the Payment Service

Build first (see above), start the Eureka Server, Config Server, API Gateway and
Parking Service (previous sections), then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl payment-service spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.payment.PaymentServiceApplication
```

This is a **mock payment gateway** — no real Stripe/PayPal/Visa integration. The
service reads its PostgreSQL connection from environment variables
(`DB_HOST`, `DB_PORT`, `PAYMENT_DB_NAME`, `PAYMENT_DB_USERNAME`,
`PAYMENT_DB_PASSWORD`) and registers with Eureka as `PAYMENT-SERVICE` on port
`8084`. Before recording a payment it verifies the reservation exists by calling
the parking service (`payment-service.verify-reservation`, default `true`). See
`docs/database-setup.md` for the PostgreSQL provisioning steps.

On a successful start you will see:

```
Tomcat started on port 8084 (http) with context path '/'
Started PaymentServiceApplication ...
```

### Endpoints

| Method | Path                                        | Description                    |
|--------|---------------------------------------------|--------------------------------|
| POST   | `/api/payments`                             | Process a mock payment         |
| GET    | `/api/payments/{id}`                        | Get a payment (incl. status)   |
| GET    | `/api/payments/reservation/{reservationId}` | List a reservation's payments  |
| GET    | `/api/payments/user/{userId}`               | List a user's payments         |
| GET    | `/api/payments/{id}/receipt`                | Get the digital receipt        |

Payment status: `PENDING`, `SUCCESS`, `FAILED`. Payment methods: `CARD`,
`CASH`, `MOCK_WALLET`. Errors: 400 invalid card data, 404 payment/reservation
not found, 409 duplicate payment for the same reservation, 503 parking service
unreachable. Full request/response samples are in `payment-service/README.md`.

### Verifying

| Check                            | URL / Command                                        |
|----------------------------------|------------------------------------------------------|
| Payment Service health (actuator) | http://localhost:8084/actuator/health               |
| Process a payment (direct)       | `curl -X POST http://localhost:8084/api/payments -H "Content-Type: application/json" -d "{\"reservationId\":1,\"userId\":1,\"amount\":500,\"paymentMethod\":\"CARD\",\"cardNumber\":\"4111111111111111\"}"` |
| Process via API Gateway          | Same POST but on `http://localhost:8080/api/payments` |
| Eureka registry (PAYMENT-SERVICE)| http://localhost:8761/eureka/apps                    |

### Example payment flow

```bash
curl -X POST http://localhost:8084/api/payments \
  -H "Content-Type: application/json" \
  -d '{"reservationId":1,"userId":1,"amount":500,"paymentMethod":"CARD","cardNumber":"4111111111111111"}'
# → 201, "status": "SUCCESS", "transactionId": "TXN-...", "maskedCardNumber": "**** **** **** 1111"

curl -X POST http://localhost:8084/api/payments \
  -H "Content-Type: application/json" \
  -d '{"reservationId":1,"userId":1,"amount":500,"paymentMethod":"CARD","cardNumber":"4000000000000002"}'
# → 409 (a SUCCESS payment already exists for reservation 1)

curl -X POST http://localhost:8084/api/payments \
  -H "Content-Type: application/json" \
  -d '{"reservationId":2,"userId":1,"amount":500,"paymentMethod":"CARD","cardNumber":"4000000000000002"}'
# → 201, "status": "FAILED" (mock decline card; retry with 4111... afterwards is allowed)

curl http://localhost:8084/api/payments/1/receipt
# → 200, receipt with receiptId/transactionId/paymentStatus
```

The mock gateway declines the configured card `4000000000000002`
(`MOCK_FAILED_CARD`) and accepts everything else; `CASH`/`MOCK_WALLET` always
succeed. Failed transactions are still stored for audit.

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
