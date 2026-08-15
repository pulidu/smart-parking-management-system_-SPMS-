# parking-service

Handles parking spaces, availability, reservations, manual status updates and
reservation cancel/release for the Smart Parking Management System (SPMS).

**Status: implemented (Phase 4).** Spring Boot 4.1.0 WebMVC service backed by
PostgreSQL, registered with Eureka as `PARKING-SERVICE`, consuming its
centralized configuration from the Config Server. The API Gateway already routes
`/api/parking/**` to it via `lb://PARKING-SERVICE`.

## Responsibilities

- **Parking owners** manage parking spaces (create, update, delete, set status)
- **Parking users** search spaces, filter by city / zone / availability, reserve,
  release and see parking status

## API endpoints

All endpoints live under `/api/parking/...`. Requests/responses are JSON.

### Parking spaces

| Method | Path                              | Description                       | Success | Errors |
|--------|-----------------------------------|-----------------------------------|---------|--------|
| POST   | `/api/parking/spaces`             | Create a parking space            | 201     | 400, 409 |
| GET    | `/api/parking/spaces`             | Search / list spaces              | 200     | -      |
| GET    | `/api/parking/spaces/{id}`        | Get a space by id                 | 200     | 404    |
| PUT    | `/api/parking/spaces/{id}`        | Update a space                    | 200     | 400, 404, 409 |
| DELETE | `/api/parking/spaces/{id}`        | Delete a space                    | 204     | 404, 409 |
| PUT    | `/api/parking/spaces/{id}/status` | Manual/IoT status update          | 200     | 400, 404 |

Space status values: `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`.

### Search / filter

```
GET /api/parking/spaces
GET /api/parking/spaces?city=Colombo
GET /api/parking/spaces?zone=Zone-A
GET /api/parking/spaces?available=true
GET /api/parking/spaces?city=Colombo&available=true
```

| Parameter   | Values                          | Effect                              |
|-------------|---------------------------------|-------------------------------------|
| `city`      | any string (case-insensitive)   | only spaces in that city            |
| `zone`      | any string                      | only spaces in that zone            |
| `available` | `true` / `false`                | `true` = `AVAILABLE` only; `false` = everything except `AVAILABLE` |

### Reservations

| Method | Path                                       | Description                     | Success | Errors |
|--------|--------------------------------------------|---------------------------------|---------|--------|
| POST   | `/api/parking/reservations`                | Create a reservation            | 201     | 400, 404, 409, 503 |
| GET    | `/api/parking/reservations/{id}`           | Get a reservation               | 200     | 404    |
| GET    | `/api/parking/reservations/user/{userId}`  | List a user's reservations      | 200     | -      |
| POST   | `/api/parking/reservations/{id}/cancel`    | Cancel a reservation            | 200     | 404, 409 |
| POST   | `/api/parking/reservations/{id}/release`   | Release a reservation (finished) | 200    | 404, 409 |

Reservation status values: `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`.

> Requests also work through the API Gateway at `http://localhost:8080/api/parking...`.

### Create a parking space

```
POST http://localhost:8083/api/parking/spaces
Content-Type: application/json
```

Request:

```json
{
  "ownerId": 1,
  "spaceNumber": "A-01",
  "location": "Level 1",
  "city": "Colombo",
  "zone": "Zone-A",
  "pricePerHour": 5.50
}
```

Response `201 Created` (new spaces always start `AVAILABLE`):

```json
{
  "id": 1,
  "ownerId": 1,
  "spaceNumber": "A-01",
  "location": "Level 1",
  "city": "Colombo",
  "zone": "Zone-A",
  "pricePerHour": 5.50,
  "status": "AVAILABLE",
  "createdAt": "2026-08-14T19:03:05.953941Z",
  "updatedAt": "2026-08-14T19:03:05.953941Z"
}
```

Validation: `ownerId` required (positive); `spaceNumber` required (1-20
letters/digits/spaces/hyphens, normalized to upper case) and unique **per
owner**; `location`, `city`, `zone` required; `pricePerHour` required (>= 0.01).

### Manual status update (simulated IoT)

```
PUT http://localhost:8083/api/parking/spaces/1/status
Content-Type: application/json

{ "status": "OCCUPIED" }
```

Lets an operator or a simulated sensor set the physical state of a space
directly (e.g. a sensor reports a car parked → `OCCUPIED`). Any of the four
valid statuses is accepted; unknown values are rejected with `400`.

### Create a reservation

```
POST http://localhost:8083/api/parking/reservations
Content-Type: application/json
```

Request:

```json
{
  "userId": 1,
  "vehicleId": 1,
  "parkingSpaceId": 1,
  "startTime": "2026-08-20T10:00:00Z",
  "endTime": "2026-08-20T12:00:00Z"
}
```

Response `201 Created`:

```json
{
  "id": 1,
  "userId": 1,
  "vehicleId": 1,
  "parkingSpaceId": 1,
  "startTime": "2026-08-20T10:00:00Z",
  "endTime": "2026-08-20T12:00:00Z",
  "status": "PENDING",
  "createdAt": "2026-08-14T19:03:31.948966Z"
}
```

### Reservation logic (create)

1. `userId` must be provided → `400` (bean validation).
2. `vehicleId` must be provided → `400` (bean validation).
3. `startTime` must be before `endTime` → else `400`.
4. **Inter-service validation** (via Eureka discovery, no hardcoded URLs):
   - the referenced **user must exist** in the User Service
     (`lb://USER-SERVICE/api/users/{id}`) → else `404`; User Service
     unreachable → `503`.
   - the referenced **vehicle must exist** in the Vehicle Service
     (`lb://VEHICLE-SERVICE/api/vehicles/{id}`) → else `404`; **and must belong
     to that user** → else `409 Conflict`; Vehicle Service unreachable → `503`.
5. Parking space must exist → else `404 Not Found`.
6. Parking space must be `AVAILABLE` → else `409 Conflict`.
7. Create the reservation with status `PENDING`.
8. Change the space status `AVAILABLE` → `RESERVED`.
9. **Prevent double reservation** — an active (`PENDING`/`CONFIRMED`)
   reservation on the same space yields `409 Conflict`.
10. All checks return the uniform `ApiError` JSON envelope.

The inter-service user/vehicle validation runs **before** the space row is
locked, so a slow or unavailable upstream cannot hold the database lock. The
reservation creation is `@Transactional` and **locks the parking space row
pessimistically** (`SELECT ... FOR UPDATE`) so two concurrent reservation
attempts on the same space serialize: exactly one succeeds, the other receives
`409 Conflict`. This is covered by a dedicated concurrency test.

### Cancel vs release

| Operation | Reservation status → | Space status → |
|-----------|----------------------|----------------|
| `cancel`  | `CANCELLED`          | `AVAILABLE`    |
| `release` | `COMPLETED`          | `AVAILABLE`    |

Both require the reservation to be active (`PENDING`/`CONFIRMED`); otherwise
`409 Conflict`. A release signals the parking is finished.

## Error handling

Errors use the same JSON envelope as the other SPMS services:

```json
{
  "timestamp": "2026-08-14T19:03:32.128965Z",
  "status": 409,
  "error": "Conflict",
  "message": "Parking space 1 is not available for reservation (status: RESERVED)",
  "path": "/api/parking/reservations",
  "fieldErrors": null
}
```

| Situation                                | Status code |
|------------------------------------------|-------------|
| Parking space / reservation not found    | 404         |
| Referenced user / vehicle not found      | 404         |
| Duplicate space number (same owner)      | 409         |
| Space not `AVAILABLE` when reserving     | 409         |
| Double reservation on the same space     | 409         |
| Vehicle does not belong to the user      | 409         |
| Cancel/release of an inactive reservation| 409         |
| Deleting a space with active reservation | 409         |
| `startTime` >= `endTime`                 | 400         |
| Invalid input / malformed body           | 400         |
| User / Vehicle Service unreachable       | 503         |
| Unexpected error                         | 500         |

## Concurrency

The inter-service user/vehicle validation (steps 4 above) happens outside the
row lock. `reservationService.create(...)` then runs inside a transaction that:

1. locks the space row (`ParkingSpaceRepository.findByIdForUpdate`, `PESSIMISTIC_WRITE`),
2. re-checks the space is `AVAILABLE`,
3. re-checks no active reservation exists (`existsByParkingSpaceIdAndStatusIn`),
4. inserts the reservation and flips the space to `RESERVED`.

A test spawns two threads that attempt to reserve the same space simultaneously;
exactly one succeeds (the other receives the `409` state exception).

## Database

Uses **PostgreSQL** - all SPMS services share the same database
(`smart_parking_db`). Connection settings come from the Config Server and can be
overridden via environment variables (the defaults match the local setup):

| Environment variable   | Default            | Purpose                        |
|------------------------|--------------------|--------------------------------|
| `DB_HOST`              | `localhost`        | PostgreSQL host                |
| `DB_PORT`              | `5432`             | PostgreSQL port                |
| `DB_NAME`              | `smart_parking_db` | Database name                  |
| `DB_USERNAME`          | `postgres`         | DB user                        |
| `DB_PASSWORD`          | (your local postgres password) | DB password          |
| `PARKING_SERVICE_PORT` | `8083`             | Service port                   |
| `EUREKA_SERVER_URL`    | `http://localhost:8761/eureka/` | Eureka registry URL |
| `CONFIG_SERVER_URL`    | `http://localhost:8888` | Config Server URL      |

The connection URL is assembled by the Config Server as
`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`. Tables are created
automatically via `ddl-auto=update` (or by hand per
[`docs/database-setup.md`](../docs/database-setup.md)):

```
parking_spaces(id BIGSERIAL PK, owner_id BIGINT NOT NULL, space_number VARCHAR(20) NOT NULL,
               location VARCHAR, city VARCHAR, zone VARCHAR(50),
               price_per_hour NUMERIC(10,2) NOT NULL, status VARCHAR(20) NOT NULL,
               created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ,
               UNIQUE(owner_id, space_number))

reservations(id BIGSERIAL PK, user_id BIGINT NOT NULL, vehicle_id BIGINT NOT NULL,
             parking_space_id BIGINT NOT NULL, start_time TIMESTAMPTZ NOT NULL,
             end_time TIMESTAMPTZ NOT NULL, status VARCHAR(20) NOT NULL,
             created_at TIMESTAMPTZ NOT NULL)
```

The `user_id`, `vehicle_id` and `parking_space_id` columns are plain references
(no foreign keys) so the service stays decoupled from the user/vehicle services.

> Dev convenience: the jar also ships with H2 on the runtime classpath so the
> service can be started without PostgreSQL for local smoke tests (see below).
> **PostgreSQL remains the configured database** — H2 is only used when
> explicitly requested via `--spring.datasource.*` overrides.

## Running

Build the whole project first (or just this module):

```bash
mvnw.cmd clean install
# or, from the project root:  mvnw.cmd -pl parking-service clean install
```

Start the infrastructure (each in its own terminal), then the service:

```bash
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar
java -jar parking-service/target/parking-service-0.0.1-SNAPSHOT.jar
```

The service registers with Eureka as `PARKING-SERVICE` on port `8083` and pulls
its datasource/JPA configuration from the Config Server. Confirm with:

| Check                          | URL / Command                              |
|--------------------------------|--------------------------------------------|
| Health (actuator)              | http://localhost:8083/actuator/health      |
| Registered in Eureka           | http://localhost:8761/eureka/apps          |
| Config delivered by Config Svr | http://localhost:8888/parking-service/default |

### Running without PostgreSQL (H2 fallback)

```bash
java -jar parking-service/target/parking-service-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url="jdbc:h2:mem:parking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa \
  --spring.datasource.password= \
  --spring.jpa.hibernate.ddl-auto=create-drop
```

## Tests

Integration tests (`src/test/java/.../ParkingServiceApplicationTests.java`) boot
the service on a random port with an in-memory H2 database and exercise every
endpoint plus the reservation business rules, the inter-service validation paths
and the concurrency guard (46 tests). The User/Vehicle Service clients are
mocked (`@MockitoBean`) so the tests run fully offline:

```bash
mvnw.cmd -pl parking-service test
```

- spaces: create 201, duplicate per owner 409, same number different owner 201,
  missing/invalid fields 400, get 200/404, search (all / city / zone /
  available=true / available=false / city+available / no match), update 200/409/404,
  delete 204/404, manual status update 200, invalid/missing status 400
- reservations: create 201 + space becomes `RESERVED`, space not found 404,
  space not available 409, missing userId/vehicleId 400, start-after-end 400,
  get 200/404, list by user (full + empty), cancel 200 + space freed, double
  cancel 409, release 200 + space freed, release when inactive 409, double
  reservation 409, reserve-cancel-reserve cycle, cancel unknown 404
- inter-service validation: user not found 404, vehicle not found 404, vehicle
  not owned by user 409, User/Vehicle Service unreachable 503
- **concurrency**: two threads reserve the same space simultaneously — exactly
  one succeeds

## Package layout

```
src/main/java/com/smartparkingmanagementsystem/parking/
├── ParkingServiceApplication.java       # Spring Boot bootstrap
├── client/                              # inter-service REST clients (lb://USER-SERVICE,
│                                        #   lb://VEHICLE-SERVICE) + clean DTOs
├── client/dto/VehicleInfoDto.java, UserInfoDto.java
├── config/RestClientConfig.java         # @LoadBalanced RestClient.Builder + timeouts
├── controller/ParkingSpaceController.java   # /api/parking/spaces**
├── controller/ReservationController.java    # /api/parking/reservations**
├── dto/                                # request/response records
├── exception/                          # custom exceptions + global handler + ApiError
├── model/ParkingSpace.java, ParkingSpaceStatus.java,
│        Reservation.java, ReservationStatus.java
├── repository/ParkingSpaceRepository.java, ReservationRepository.java
└── service/ParkingSpaceService.java, ReservationService.java
```
