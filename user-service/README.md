# user-service

Handles user accounts, authentication and profiles for the Smart Parking
Management System (SPMS).

**Status: implemented (Phase 3).** Spring Boot 4.1.0 WebMVC service backed by
PostgreSQL, registered with Eureka as `USER-SERVICE`, consuming its centralized
configuration from the Config Server.

## Responsibilities

- User registration
- User authentication / login
- View and update user profiles
- Booking-history placeholder endpoint
- User roles: `DRIVER`, `OWNER`, `ADMIN`

## API endpoints

All endpoints live under `/api/users`. Requests/responses are JSON.
`id` is returned from `POST /api/users` and echoed in responses.

| Method | Path                     | Description                       | Success | Errors |
|--------|--------------------------|-----------------------------------|---------|--------|
| POST   | `/api/users`             | Register a user                   | 201     | 400, 409 |
| POST   | `/api/users/login`       | Authenticate (email + password)   | 200     | 400, 401 |
| GET    | `/api/users/{id}`        | View a user profile               | 200     | 404    |
| PUT    | `/api/users/{id}`        | Update a user profile             | 200     | 400, 404, 409 |
| GET    | `/api/users/{id}/bookings` | Booking history (placeholder)   | 200     | 404    |

> Requests also work through the API Gateway at `http://localhost:8080/api/users...`
> (routes to `USER-SERVICE` via Eureka discovery).

### Register a user

```
POST http://localhost:8081/api/users
Content-Type: application/json
```

Request:

```json
{
  "name": "Alice Driver",
  "email": "alice@example.com",
  "password": "secret123",
  "phone": "+1-555-0100",
  "role": "DRIVER"
}
```

Response `201 Created` (note: the password hash is never returned):

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

Validation: `name` required; `email` required, valid and unique; `password`
required (min 6 chars); `phone` optional but format-validated when present;
`role` required and must be one of `DRIVER | OWNER | ADMIN`.

### Login

```
POST http://localhost:8081/api/users/login
Content-Type: application/json
```

Request:

```json
{
  "email": "alice@example.com",
  "password": "secret123"
}
```

Response `200 OK`:

```json
{
  "token": null,
  "user": {
    "id": 1,
    "name": "Alice Driver",
    "email": "alice@example.com",
    "phone": "+1-555-0100",
    "role": "DRIVER",
    "createdAt": "2026-08-14T14:12:37.111561Z",
    "updatedAt": "2026-08-14T14:12:37.111561Z"
  }
}
```

`token` is intentionally `null` for now and reserved for a future JWT/security
layer. Login currently performs a simple credential check (BCrypt) without
issuing a session/token — the design is extensible for hardening later.

Wrong password or unknown email → `401`.

### Get profile

```
GET http://localhost:8081/api/users/1
```

Response `200 OK` with the profile JSON (same shape as register). Unknown id → `404`.

### Update profile

```
PUT http://localhost:8081/api/users/1
Content-Type: application/json
```

Request:

```json
{
  "name": "Alice G. Driver",
  "email": "alice@example.com",
  "phone": "+44-20-7946-0958"
}
```

Response `200 OK` with the updated profile. `password` may be included to change
the password (optional). Changing the email to one already in use → `409`.

### Booking history (placeholder)

```
GET http://localhost:8081/api/users/1/bookings
```

Response `200 OK`:

```json
{
  "bookings": []
}
```

Always returns an empty list until the parking/booking service is implemented.
The response contract (`BookingResponse`) already defines the future shape so
clients won't break when real data arrives.

## Error handling

Errors are returned in a uniform JSON envelope (matching the API Gateway shape):

```json
{
  "timestamp": "2026-08-14T14:12:46.100082Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/users/login",
  "fieldErrors": null
}
```

| Situation       | Status code |
|-----------------|-------------|
| User not found  | 404         |
| Duplicate email | 409         |
| Invalid input   | 400         |
| Invalid login   | 401         |
| Unexpected error| 500         |

For validation failures, `fieldErrors` maps field names to messages (e.g.
`{"email": "email must be a valid email address"}`).

## Roles

| Role    | Meaning                         |
|---------|---------------------------------|
| DRIVER  | Parks and pays for sessions     |
| OWNER   | Manages parking spaces          |
| ADMIN   | Full system administration      |

Roles are stored as a string enum. Authorization rules are not enforced yet —
that is part of the future security layer.

## Database

Uses **PostgreSQL**. Connection settings come from environment variables and are
**never hardcoded** in source control:

| Environment variable | Default   | Purpose                        |
|----------------------|-----------|--------------------------------|
| `DB_HOST`            | `localhost` | PostgreSQL host              |
| `DB_PORT`            | `5432`      | PostgreSQL port              |
| `USER_DB_NAME`       | `user_db`   | Database name                |
| `USER_DB_USERNAME`   | `user_service` | DB user                   |
| `USER_DB_PASSWORD`   | (empty)     | DB password                  |
| `USER_SERVICE_PORT`  | `8081`      | Service port                 |
| `EUREKA_SERVER_URL`  | `http://localhost:8761/eureka/` | Eureka registry URL |
| `CONFIG_SERVER_URL`  | `http://localhost:8888` | Config Server URL    |

The connection URL is assembled by the Config Server as
`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${USER_DB_NAME}`.

> Dev convenience: the jar also ships with H2 on the runtime classpath so the
> service can be started without PostgreSQL for local smoke tests (see below).
> **PostgreSQL remains the configured database** — H2 is only used when explicitly
> requested via `--spring.datasource.*` overrides.

### Database / schema setup

Full instructions (creating the role, database and tables) are in
[`docs/database-setup.md`](../docs/database-setup.md). In development the schema
is created automatically by Hibernate (`spring.jpa.hibernate.ddl-auto=update`),
with the `users` table mapping from the `User` entity:

```
users(id BIGSERIAL PK, name VARCHAR, email VARCHAR UNIQUE, password VARCHAR,
      phone VARCHAR, role VARCHAR, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ)
```

## Running

Build the whole project first (or just this module):

```bash
mvnw.cmd clean install
# or, from the project root:  mvnw.cmd -pl user-service clean install
```

Start the infrastructure (each in its own terminal), then the service:

```bash
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
```

The service registers with Eureka as `USER-SERVICE` on port `8081` and pulls its
datasource/JPA configuration from the Config Server. Confirm with:

| Check                          | URL / Command                              |
|--------------------------------|--------------------------------------------|
| Health (actuator)              | http://localhost:8081/actuator/health      |
| Registered in Eureka           | http://localhost:8761/eureka/apps          |
| Config delivered by Config Svr | http://localhost:8888/user-service/default |

### Running without PostgreSQL (H2 fallback)

If no PostgreSQL is available, override the datasource on the command line:

```bash
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url="jdbc:h2:mem:users;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa \
  --spring.datasource.password= \
  --spring.jpa.hibernate.ddl-auto=create-drop
```

## Tests

Integration tests (`src/test/java/.../UserServiceApplicationTests.java`) boot the
service on a random port with an in-memory H2 database and exercise every
endpoint plus the error cases (17 tests):

```bash
mvnw.cmd -pl user-service test
```

- `registerUserReturns201AndPersists`, duplicate → 409, invalid email → 400,
  missing name → 400, short password → 400, invalid role → 400
- login success → 200, wrong password → 401, unknown email → 401
- get by id → 200 / 404, update → 200, update to taken email → 409, update 404
- bookings placeholder → 200 empty, bookings for unknown user → 404

## Package layout

```
src/main/java/com/smartparkingmanagementsystem/user/
├── UserServiceApplication.java     # Spring Boot bootstrap
├── config/PasswordConfig.java      # BCryptPasswordEncoder bean
├── controller/UserController.java  # REST endpoints
├── dto/                            # request/response records
├── exception/                      # custom exceptions + global handler + ApiError
├── model/User.java, Role.java      # JPA entity + roles enum
├── repository/UserRepository.java  # Spring Data JPA repository
└── service/UserService.java        # business logic
```
