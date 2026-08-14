# Database Setup (PostgreSQL)

This document describes how to provision PostgreSQL for the SPMS microservices.
Each service keeps its own database so services stay decoupled.

> During development Hibernate manages the schema automatically
> (`spring.jpa.hibernate.ddl-auto=update` for the services). Production should
> replace this with explicit Flyway/Liquibase migrations. The sections below
> document what the services expect so the database can also be created by hand.

## Prerequisites

- PostgreSQL 14+ installed and running on `localhost:5432`
  (or set the `DB_HOST` / `DB_PORT` environment variables).
- The Postgres `psql` client on your `PATH`.

## 1. Create the database role and database

Connect as a superuser (e.g. `postgres`) and run:

```sql
-- user-service
CREATE ROLE user_service WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE user_db OWNER user_service;

-- vehicle-service
CREATE ROLE vehicle_service WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE vehicle_db OWNER vehicle_service;

-- parking-service
CREATE ROLE parking_service WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE parking_db OWNER parking_service;

-- payment-service
CREATE ROLE payment_service WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE payment_db OWNER payment_service;
```

## 2. Configure the service connection

The service reads its connection settings from environment variables (no
credentials are committed to the repository):

| Variable            | Value                                        |
|---------------------|----------------------------------------------|
| `DB_HOST`           | `localhost`                                  |
| `DB_PORT`           | `5432`                                       |
| `USER_DB_NAME`      | `user_db`                                    |
| `USER_DB_USERNAME`  | `user_service`                               |
| `USER_DB_PASSWORD`  | (the password you set above)                 |
| `VEHICLE_DB_NAME`   | `vehicle_db`                                 |
| `VEHICLE_DB_USERNAME` | `vehicle_service`                           |
| `VEHICLE_DB_PASSWORD` | (the password you set above)               |
| `PARKING_DB_NAME`   | `parking_db`                                 |
| `PARKING_DB_USERNAME` | `parking_service`                         |
| `PARKING_DB_PASSWORD` | (the password you set above)             |
| `PAYMENT_DB_NAME`   | `payment_db`                                 |
| `PAYMENT_DB_USERNAME` | `payment_service`                         |
| `PAYMENT_DB_PASSWORD` | (the password you set above)             |

For example, in a terminal before starting the service:

```bash
# Windows (PowerShell)
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:USER_DB_NAME = "user_db"
$env:USER_DB_USERNAME = "user_service"
$env:USER_DB_PASSWORD = "change-me"
$env:VEHICLE_DB_NAME = "vehicle_db"
$env:VEHICLE_DB_USERNAME = "vehicle_service"
$env:VEHICLE_DB_PASSWORD = "change-me"
$env:PARKING_DB_NAME = "parking_db"
$env:PARKING_DB_USERNAME = "parking_service"
$env:PARKING_DB_PASSWORD = "change-me"
$env:PAYMENT_DB_NAME = "payment_db"
$env:PAYMENT_DB_USERNAME = "payment_service"
$env:PAYMENT_DB_PASSWORD = "change-me"

# Linux / macOS
export DB_HOST=localhost DB_PORT=5432 USER_DB_NAME=user_db \
       USER_DB_USERNAME=user_service USER_DB_PASSWORD=change-me \
       VEHICLE_DB_NAME=vehicle_db VEHICLE_DB_USERNAME=vehicle_service \
       VEHICLE_DB_PASSWORD=change-me \
       PARKING_DB_NAME=parking_db PARKING_DB_USERNAME=parking_service \
       PARKING_DB_PASSWORD=change-me \
       PAYMENT_DB_NAME=payment_db PAYMENT_DB_USERNAME=payment_service \
       PAYMENT_DB_PASSWORD=change-me
```

The Config Server assembles each service's JDBC URL as
`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${SERVICE_DB_NAME}` (e.g.
`USER_DB_NAME` for the user service, `VEHICLE_DB_NAME` for the vehicle service,
`PARKING_DB_NAME` for the parking service, `PAYMENT_DB_NAME` for the payment
service).

## 3. Schema

The `user-service` owns the `users` table. With
`spring.jpa.hibernate.ddl-auto=update` it is created automatically from the
`User` entity. The equivalent DDL is:

```sql
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    phone      VARCHAR(255),
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users (email);
```

Notes:

- `email` has a unique constraint — the service also checks for duplicates and
  returns `409 Conflict` before the database is even hit (the constraint is the
  safety net for race conditions).
- `password` stores a **BCrypt hash**, never the plain text.
- `role` is one of `DRIVER`, `OWNER`, `ADMIN` (stored as a string).

### vehicle-service — `vehicles` table

```sql
CREATE TABLE IF NOT EXISTS vehicles (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    vehicle_number VARCHAR(20)  NOT NULL UNIQUE,
    vehicle_type   VARCHAR(20)  NOT NULL,
    brand          VARCHAR(255) NOT NULL,
    model          VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    entry_time     TIMESTAMPTZ,
    exit_time      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicles_vehicle_number ON vehicles (vehicle_number);
```

Notes:

- `user_id` references the owning user (no foreign key yet — the user service
  owns users; ownership validation may be added in a later phase).
- `vehicle_number` is unique — duplicates return `409 Conflict`.
- `status` is `OUTSIDE` or `INSIDE`; `entry_time` / `exit_time` track the most
  recent entry/exit events from the simulated entry/exit endpoints.

### parking-service — `parking_spaces` and `reservations` tables

```sql
CREATE TABLE IF NOT EXISTS parking_spaces (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT        NOT NULL,
    space_number   VARCHAR(20)   NOT NULL,
    location       VARCHAR(255)  NOT NULL,
    city           VARCHAR(255)  NOT NULL,
    zone           VARCHAR(50)   NOT NULL,
    price_per_hour NUMERIC(10,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_parking_spaces_owner_space_number
    ON parking_spaces (owner_id, space_number);

CREATE TABLE IF NOT EXISTS reservations (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    vehicle_id       BIGINT       NOT NULL,
    parking_space_id BIGINT       NOT NULL,
    start_time       TIMESTAMPTZ  NOT NULL,
    end_time         TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL
);
```

Notes:

- `space_number` is unique **per owner** (`owner_id, space_number`) — duplicates
  return `409 Conflict`.
- `status` is `AVAILABLE`, `RESERVED`, `OCCUPIED` or `MAINTENANCE`.
- `reservations.status` is `PENDING`, `CONFIRMED`, `CANCELLED` or `COMPLETED`.
- `user_id`, `vehicle_id` and `parking_space_id` are plain references (no foreign
  keys) so the parking service stays decoupled from the user/vehicle services.

### payment-service — `payments` table

```sql
CREATE TABLE IF NOT EXISTS payments (
    id             BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    amount         NUMERIC(10,2) NOT NULL,
    payment_method VARCHAR(20)   NOT NULL,
    transaction_id VARCHAR(50)   NOT NULL UNIQUE,
    status         VARCHAR(20)   NOT NULL,
    payment_date   TIMESTAMPTZ   NOT NULL,
    card_last4     VARCHAR(4),
    created_at     TIMESTAMPTZ   NOT NULL
);

-- Race-condition safety net for the duplicate-payment guard: only one
-- successful payment may exist per reservation (failed payments may be retried).
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_success_per_reservation
    ON payments (reservation_id) WHERE status = 'SUCCESS';
```

Notes:

- `status` is `PENDING`, `SUCCESS` or `FAILED`.
- `payment_method` is `CARD`, `CASH` or `MOCK_WALLET`.
- The full card number is **never stored** — only `card_last4` (used to produce
  masked values like `**** **** **** 1111` in responses).
- `transaction_id` is unique — each stored payment gets its own id.
- The duplicate-payment check runs in the service layer (a `PENDING`/`SUCCESS`
  payment blocks a new one for the same reservation); the partial unique index
  above is the PostgreSQL safety net for concurrent requests.
- `reservation_id` and `user_id` are plain references (no foreign keys) so the
  payment service stays decoupled from the parking/user services.

## 4. Verify

Start the service and check its health indicator (visible on
`http://localhost:8081/actuator/health`), or connect directly:

```bash
psql -h localhost -U user_service -d user_db -c "\d users"
```
