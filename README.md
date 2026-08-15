# Smart Parking Management System

A backend-only, microservice-based platform that manages parking operations end to
end: users, their vehicles, parking spaces, reservations and payments. All
interaction happens through REST APIs exposed by a single API Gateway.

---

## Project Overview

The Smart Parking Management System (SPMS) is built as **seven Spring Boot / Spring
Cloud microservices** that each own a single business domain:

| Module             | Domain                          |
|--------------------|---------------------------------|
| Eureka Server      | Service discovery / registry    |
| Config Server      | Centralized external config     |
| API Gateway        | Single REST entry point         |
| User Service       | Users, authentication, profiles |
| Vehicle Service    | Vehicles + entry/exit state     |
| Parking Service    | Parking spaces + reservations   |
| Payment Service    | Mock payments + receipts        |

Every request flows through the API Gateway (`http://localhost:8080`), which routes
to the target service through Eureka service discovery — the backend services are
never called directly by clients. The backend is intentionally UI-free: all features
are exercised through REST.

---

## Business Problem

Parking operations in many real deployments are fragmented and manual:

- **No central booking system** — parking spaces are allocated ad-hoc, so double
  booking and idle capacity are common.
- **No vehicle lifecycle tracking** — there is no record of when a vehicle enters or
  exits a facility.
- **No connected payment record** — drivers get no digital receipt and operators
  cannot reconcile income against reservations.
- **Monolithic / tightly coupled systems** — a single large application is hard to
  change, test and scale, and teams cannot release independently.
- **Siloed data** — users, vehicles, spaces and payments live in separate systems
  with no coherent API.

---

## Proposed Solution

SPMS applies a **microservice architecture** with clear bounded contexts:

- Each business capability (users, vehicles, parking, payments) is an independent,
  deployable **Spring Boot service**. For local coursework all services share a
  single PostgreSQL database (`smart_parking_db`), each owning its own tables.
- Services register with **Eureka** and discover each other through **load-balanced
  service names** (`lb://USER-SERVICE`), so no hardcoded host/port is baked in.
- A **Config Server** centralizes environment-specific configuration.
- A **Spring Cloud Gateway** is the single public entry point that routes
  `/api/{users,vehicles,parking,payments}/**` to the right service.
- Inter-service validation is explicit and synchronous with timeouts: the parking
  service verifies the user and vehicle referenced by a reservation; the payment
  service verifies the reservation before charging.
- Every service exposes a **consistent JSON error contract** and proper HTTP status
  codes, with bean validation on all inputs.

---

## Architecture

```
                        ┌──────────────────┐
                        │   api-gateway     │  (Spring Cloud Gateway - single entry point)
                        └────────┬─────────┘
                                 │ routes by service name (load-balanced via Eureka)
        ┌───────────────┬────────┴─────────┬───────────────┬───────────────┐
        │               │                  │               │               │
┌───────▼──────┐ ┌──────▼───────┐ ┌────────▼───────┐ ┌─────▼────────┐
│ user-service │ │vehicle-service│ │parking-service│ │payment-service│
└───────┬──────┘ └──────┬───────┘ └────────┬───────┘ └─────┬────────┘
        │               │                  │               │
        └───────────────┴────────┬─────────┴───────────────┘
                                 │
                       ┌─────────▼─────────┐        ┌───────────────────┐
                       │   eureka-server   │◄──────►│   config-server    │
                       │  (port 8761)      │        │ (centralized config)
                       └───────────────────┘        └───────────────────┘
```

### Inter-service communication

Services never call each other through hardcoded host/ports. Backend-to-backend
calls go through **Eureka service discovery** using the load-balanced service name
scheme `lb://EUREKA-SERVICE-ID`:

```
Gateway
   |
   +--> User Service
   |
   +--> Vehicle Service
   |
   +--> Parking Service
              |
              +--> Vehicle Service      (validate vehicle exists + belongs to user)
              |
              +--> User Service         (validate user exists)

Payment Service
   |
   +--> Parking Service                 (validate reservation exists)
```

- **Parking Service → User Service**: before creating a reservation, the referenced
  user must exist (`GET /api/users/{id}`). Unknown user → `404`, User Service
  unreachable → `503`.
- **Parking Service → Vehicle Service**: the referenced vehicle must exist *and*
  belong to the requesting user (`GET /api/vehicles/{id}`). Unknown vehicle → `404`,
  owned by someone else → `409`, Vehicle Service unreachable → `503`.
- **Payment Service → Parking Service**: before recording a payment, the reservation
  must exist (`GET /api/parking/reservations/{id}`). Unknown reservation → `404`,
  Parking Service unreachable → `503`.

Calls are synchronous with explicit connect/read timeouts (default 3s/5s,
configurable via `*.client.connect-timeout-ms` / `*.client.read-timeout-ms`). The
dependency graph is acyclic, and services only exchange clean client DTOs.

---

## Technologies

| Concern          | Technology                                      |
|------------------|-------------------------------------------------|
| Language         | Java 21                                         |
| Framework        | Spring Boot 4.1.0                               |
| Microservices    | Spring Cloud 2025.1.2 (Oakwood)                 |
| Service Registry | Spring Cloud Netflix Eureka Server 5.0.2        |
| Config Server    | Spring Cloud Config Server 5.0.4                |
| API Gateway      | Spring Cloud Gateway 5.0.2 (WebFlux)            |
| Persistence      | Spring Data JPA + Hibernate (+ PostgreSQL)      |
| Validation       | Bean Validation (Hibernate Validator)           |
| Build tool       | Apache Maven 3.9.16 (Maven Wrapper `mvnw`)      |
| API style        | REST (JSON), consistent JSON error contract     |
| Tests            | JUnit 5, MockMvc, Mockito (hermetic, offline)   |

---

## Microservices

### Eureka Server
Standalone Netflix Eureka service registry on port `8761`. Every other service
registers here and the gateway resolves `lb://` targets from the registry. The
server itself does not self-register (`register-with-eureka=false`,
`fetch-registry=false`). Dashboard: `http://localhost:8761/`.

### Config Server
Spring Cloud Config Server on port `8888` using a **native repository**
(`classpath:/config`). It serves one YAML file per service plus a shared
`application.yml` (Eureka defaults). Database connection settings point every
service at the shared `smart_parking_db`, default to the local `postgres` user,
and can be overridden via environment variables. Example:
`http://localhost:8888/user-service/default`.

### API Gateway
Spring Cloud Gateway (WebFlux) on port `8080`. The single public entry point. Routes
are defined centrally in `config-server/src/main/resources/config/api-gateway.yml`
and are all `lb://` based, so no backend host/port is hardcoded:

| Route                | Target (Eureka ID) |
|----------------------|--------------------|
| `/api/users/**`      | `lb://USER-SERVICE` |
| `/api/vehicles/**`   | `lb://VEHICLE-SERVICE` |
| `/api/parking/**`    | `lb://PARKING-SERVICE` |
| `/api/payments/**`   | `lb://PAYMENT-SERVICE` |

### User Service
Port `8081`, Eureka ID `USER-SERVICE`. Manages users (drivers, owners, admins),
registration, login and profiles. Passwords are stored as **BCrypt hashes** and never
returned. Email is lowercased and unique. Owns the `users` table.

### Vehicle Service
Port `8082`, Eureka ID `VEHICLE-SERVICE`. Manages vehicles per user with a simulated
**entry/exit state machine** (`OUTSIDE` ↔ `INSIDE`). Owns the `vehicles` table;
vehicle numbers are globally unique.

### Parking Service
Port `8083`, Eureka ID `PARKING-SERVICE`. Manages parking spaces (CRUD + search by
city/zone/availability + manual IoT-style status updates) and reservations (create,
get, list, cancel, release). Before creating a reservation it validates the
referenced user and vehicle via the User / Vehicle Services. Owns the
`parking_spaces` and `reservations` tables. The reservation flow is transactional and
locks the space row (`PESSIMISTIC_WRITE`) so concurrent attempts serialize.

### Payment Service
Port `8084`, Eureka ID `PAYMENT-SERVICE`. A **mock payment gateway** — no real
provider integration. It validates mock card data (format + Luhn), verifies the
reservation exists via the Parking Service, guards against duplicate payments and
settles deterministically (`CARD` fails only for the configured mock-decline card
`4000000000000002`; `CASH`/`MOCK_WALLET` always succeed). Only the last four digits
of the card are stored. Owns the `payments` table.

---

## Service Ports

| Service          | Eureka ID       | Port  | Purpose                                  |
|------------------|-----------------|-------|------------------------------------------|
| Eureka Server    | — (standalone)  | 8761  | Service registry / dashboard             |
| Config Server    | `CONFIG-SERVER` | 8888  | Centralized configuration                |
| API Gateway      | `API-GATEWAY`   | 8080  | Single REST entry point                  |
| User Service     | `USER-SERVICE`  | 8081  | Users, auth, profiles                    |
| Vehicle Service  | `VEHICLE-SERVICE`| 8082 | Vehicles + entry/exit                    |
| Parking Service  | `PARKING-SERVICE`| 8083 | Parking spaces + reservations            |
| Payment Service  | `PAYMENT-SERVICE`| 8084 | Mock payments + receipts                 |

---

## Database Design

For local coursework all services share a single PostgreSQL database
(`smart_parking_db`); each service owns its own tables within it. Cross-service
references are plain identifiers (no foreign keys) so the parking and payment
services stay decoupled from the user/vehicle services.

| Service          | Database           | Tables                  | Ownership notes                                |
|------------------|--------------------|-------------------------|------------------------------------------------|
| User Service     | `smart_parking_db` | `users`                 | email unique; password = BCrypt hash            |
| Vehicle Service  | `smart_parking_db` | `vehicles`              | vehicle_number unique                          |
| Parking Service  | `smart_parking_db` | `parking_spaces`, `reservations` | space_number unique per owner; reservations reference user/vehicle ids |
| Payment Service  | `smart_parking_db` | `payments`              | transaction_id unique; only card_last4 stored  |

Connection settings are served by the Config Server and default to the local
setup (`localhost:5432/smart_parking_db`, user `postgres`). They can be
overridden through the `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` and
`DB_PASSWORD` environment variables. Schema is managed by Hibernate
(`ddl-auto=update`); production should migrate to Flyway/Liquibase. The full DDL
and provisioning steps are in
[`docs/database-setup.md`](docs/database-setup.md).

---

## API Endpoints

All endpoints are accessed through the gateway (`http://localhost:8080`).

### User Service
| Method | Path                    | Description                       |
|--------|-------------------------|-----------------------------------|
| POST   | `/api/users`            | Register a user                   |
| POST   | `/api/users/login`      | Authenticate (email + password)   |
| GET    | `/api/users/{id}`       | Get a user profile                |
| PUT    | `/api/users/{id}`       | Update a user profile             |
| GET    | `/api/users/{id}/bookings` | Booking history (placeholder)  |

### Vehicle Service
| Method | Path                          | Description                    |
|--------|-------------------------------|--------------------------------|
| POST   | `/api/vehicles`               | Register a vehicle             |
| GET    | `/api/vehicles/{id}`          | Get a vehicle                  |
| GET    | `/api/vehicles/user/{userId}` | List a user's vehicles         |
| PUT    | `/api/vehicles/{id}`          | Update a vehicle               |
| DELETE | `/api/vehicles/{id}`          | Delete a vehicle (204)         |
| POST   | `/api/vehicles/{id}/entry`    | Simulate entry (→ `INSIDE`)    |
| POST   | `/api/vehicles/{id}/exit`     | Simulate exit (→ `OUTSIDE`)    |

### Parking Service — Spaces
| Method | Path                                  | Description                  |
|--------|---------------------------------------|------------------------------|
| POST   | `/api/parking/spaces`                 | Create a parking space       |
| GET    | `/api/parking/spaces`                 | Search/filter spaces         |
| GET    | `/api/parking/spaces?city={city}`     | Search by city               |
| GET    | `/api/parking/spaces?zone={zone}`     | Search by zone               |
| GET    | `/api/parking/spaces?available=true`  | Search available spaces      |
| GET    | `/api/parking/spaces/{id}`            | Get a space                  |
| PUT    | `/api/parking/spaces/{id}`            | Update a space               |
| DELETE | `/api/parking/spaces/{id}`            | Delete a space (204)         |
| PUT    | `/api/parking/spaces/{id}/status`     | Manual/IoT status update     |

### Parking Service — Reservations
| Method | Path                                        | Description                  |
|--------|---------------------------------------------|------------------------------|
| POST   | `/api/parking/reservations`                 | Reserve a space              |
| GET    | `/api/parking/reservations/{id}`            | Get a reservation            |
| GET    | `/api/parking/reservations/user/{userId}`   | List a user's reservations   |
| POST   | `/api/parking/reservations/{id}/cancel`     | Cancel a reservation         |
| POST   | `/api/parking/reservations/{id}/release`    | Release a reservation        |

### Payment Service
| Method | Path                                        | Description                  |
|--------|---------------------------------------------|------------------------------|
| POST   | `/api/payments`                             | Process a mock payment       |
| GET    | `/api/payments/{id}`                        | Get a payment                |
| GET    | `/api/payments/reservation/{reservationId}` | Payments for a reservation   |
| GET    | `/api/payments/user/{userId}`               | Payments for a user          |
| GET    | `/api/payments/{id}/receipt`                | Get the digital receipt      |

---

## Running the Project

**Prerequisites**

- JDK 21 installed and on the `PATH`.
- PostgreSQL running (or run with the in-memory H2 fallback described below).
- No Maven install needed — the Maven Wrapper (`mvnw.cmd` on Windows,
  `./mvnw` on Linux/macOS) downloads Maven 3.9.16 on first use.

**Build everything**

```bash
mvnw.cmd clean install     # Windows
./mvnw clean install       # Linux / macOS
```

**Startup order (required)**

Start each service in its own terminal window, in this exact order:

| Step | Service        | Command                                       | Port |
|------|----------------|-----------------------------------------------|------|
| 1    | Eureka Server  | `mvnw.cmd -pl eureka-server spring-boot:run`  | 8761 |
| 2    | Config Server  | `mvnw.cmd -pl config-server spring-boot:run`  | 8888 |
| 3    | User Service   | `mvnw.cmd -pl user-service spring-boot:run`   | 8081 |
| 4    | Vehicle Service| `mvnw.cmd -pl vehicle-service spring-boot:run`| 8082 |
| 5    | Parking Service| `mvnw.cmd -pl parking-service spring-boot:run`| 8083 |
| 6    | Payment Service| `mvnw.cmd -pl payment-service spring-boot:run`| 8084 |
| 7    | API Gateway    | `mvnw.cmd -pl api-gateway spring-boot:run`    | 8080 |

**Database**: the services connect to the shared `smart_parking_db` on
`localhost:5432` as `postgres` by default (see
[`docs/database-setup.md`](docs/database-setup.md)) — only set the `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USERNAME` or `DB_PASSWORD` environment variables to
override these. If you do not have PostgreSQL, you can start each service with
in-memory H2 instead:

```powershell
mvnw.cmd -pl user-service spring-boot:run `
  --spring.datasource.url=jdbc:h2:mem:appdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 `
  --spring.datasource.driver-class-name=org.h2.Driver `
  --spring.datasource.username=sa --spring.datasource.password= `
  --spring.jpa.hibernate.ddl-auto=create-drop
```
**Verify**

| Check                     | URL                                              |
|---------------------------|--------------------------------------------------|
| Eureka dashboard          | `http://localhost:8761/`                         |
| Eureka registry           | `http://localhost:8761/eureka/apps`              |
| Config for a service      | `http://localhost:8888/{service}/default`        |
| Gateway health            | `http://localhost:8080/actuator/health`          |
| Gateway routes            | `http://localhost:8080/actuator/gateway/routes`  |
| Service health            | `http://localhost:808{1..4}/actuator/health`     |

---

## Postman Testing

A complete, importable Postman collection is included:
[`postman_collection.json`](./postman_collection.json).

- Import the file in Postman (**Import → Upload Files**).
- All business requests use the `{{baseUrl}}` collection variable
  (default `http://localhost:8080` — the API Gateway) and must be run **in order**
  so earlier requests create the users/vehicles/spaces/reservations the later ones
  reference (ids start at 1).
- Folders: **User Service**, **Vehicle Service**, **Parking Service**,
  **Reservation**, **Payment Service**, **Error Cases**, and an
  **Infrastructure** folder with Eureka/Config/Gateway health checks.
- The **Error Cases** folder demonstrates the expected 400/401/404/409 responses.

---

## Postman Collection

[View Postman Collection](./postman_collection.json)

## Screenshots

### Screenshot 1

The Eureka dashboard showing the microservices registered with the service
registry.

![Screenshot 1](./docs/screenshots/Yureka%20Dashboard%2001.png)

### Screenshot 2

The Eureka dashboard listing the registered service instances and their status.

![Screenshot 2](./docs/screenshots/Yureka%20Dashboard%2002.png)

---

## Eureka Dashboard

After all seven services are running, open
[http://localhost:8761/](http://localhost:8761/) — the dashboard shows the
registered application instances (`API-GATEWAY`, `CONFIG-SERVER`, `USER-SERVICE`,
`VEHICLE-SERVICE`, `PARKING-SERVICE`, `PAYMENT-SERVICE`; the Eureka server itself
does not self-register).

![Eureka Dashboard](./docs/screenshots/Yureka%20Dashboard.png)

---

## Error Handling

Every service returns errors as a consistent JSON document (`ApiError`):

```json
{
  "timestamp": "2026-08-15T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 999",
  "path": "/api/users/999"
}
```

Validation failures additionally include `fieldErrors`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/users",
  "fieldErrors": { "email": "email must be a valid email address" }
}
```

| Status | Meaning                                                        |
|--------|----------------------------------------------------------------|
| 400    | Invalid input (validation, malformed JSON, bad enum, invalid reservation time or card data) |
| 401    | Invalid login credentials (User Service)                       |
| 404    | Resource not found (user/vehicle/space/reservation/payment), or a referenced resource is missing |
| 409    | Duplicate record (email/vehicle number/space number) or invalid state transition (double entry/exit, double reservation/payment, space not available) |
| 503    | An upstream service is unreachable during inter-service validation |
| 500    | Unexpected internal error (fallback)                           |

Duplicate and state-transition rules are enforced twice: an explicit service-layer
check (friendly `409` message) plus a database constraint / pessimistic lock as the
race-condition safety net.

---

## Business Flow

```
User
  ↓
Gateway            (http://localhost:8080 - single entry point)
  ↓
Parking            (space availability, owner, zones)
  ↓
Reservation        (space reserved, user + vehicle validated via discovery)
  ↓
Payment            (mock gateway, duplicate guard, Luhn + format checks)
  ↓
Receipt            (digital receipt with transaction id + payment status)
```

Walkthrough:

1. A **user** registers and logs in (User Service).
2. The **gateway** routes every request to the owning service via Eureka.
3. The **parking service** manages spaces; the driver searches and picks an available
   space.
4. A **reservation** is created — the parking service verifies (through discovery)
   that the user and vehicle exist and that the vehicle belongs to the user, then
   locks the space so it cannot be double-booked.
5. A **payment** is created for the reservation — the payment service verifies the
   reservation exists, validates the card, blocks duplicates and settles the mock
   transaction.
6. A **receipt** is available for every stored payment.

---

## Future Improvements

The current implementation is a solid, fully working backend. Natural next steps:

- **Real IoT integration** — replace the manual `/status` updates and simulated
  entry/exit with actual sensors (ANPR cameras, barrier controllers, occupancy
  sensors) pushing events to the parking service.
- **Real payment gateway** — swap the mock gateway for a real provider (Stripe,
  PayPal) with idempotency keys, webhooks and reconciliation.
- **JWT security** — the `login` response already reserves a `token` field; wire in
  Spring Security + JWT and protect the gateway routes and cross-service calls.
- **Docker** — containerize every service and orchestrate with `docker compose`
  (or Kubernetes), replacing the manual startup order.
- **Kafka** — make inter-service validation async (e.g. reservation-created /
  payment-completed events) and add event sourcing for audit trails.
- **Cloud deployment** — deploy to AWS/Azure/GCP using managed PostgreSQL, a managed
  registry, and CI/CD pipelines.
- **Database migrations** — replace `ddl-auto=update` with Flyway/Liquibase.
- **API documentation** — add OpenAPI/Swagger on the gateway.
