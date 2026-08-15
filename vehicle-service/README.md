# vehicle-service

Handles vehicle registration, management and simulated entry/exit status tracking
for the Smart Parking Management System (SPMS).

**Status: implemented (Phase 3).** Spring Boot 4.1.0 WebMVC service backed by
PostgreSQL, registered with Eureka as `VEHICLE-SERVICE`, consuming its
centralized configuration from the Config Server.

## Responsibilities

- Register a vehicle (owned by a user)
- Update / delete / retrieve vehicles
- List vehicles belonging to a user
- Simulate vehicle entry and exit (status: `OUTSIDE` / `INSIDE`)

## API endpoints

All endpoints live under `/api/vehicles`. Requests/responses are JSON.

| Method | Path                         | Description                     | Success | Errors |
|--------|------------------------------|---------------------------------|---------|--------|
| POST   | `/api/vehicles`              | Register a vehicle              | 201     | 400, 409 |
| GET    | `/api/vehicles/{id}`         | Get a vehicle by id             | 200     | 404    |
| GET    | `/api/vehicles/user/{userId}`| List a user's vehicles          | 200     | -      |
| PUT    | `/api/vehicles/{id}`         | Update a vehicle                | 200     | 400, 404, 409 |
| DELETE | `/api/vehicles/{id}`         | Delete a vehicle                | 204     | 404    |
| POST   | `/api/vehicles/{id}/entry`   | Simulate vehicle entry          | 200     | 404, 409 |
| POST   | `/api/vehicles/{id}/exit`    | Simulate vehicle exit           | 200     | 404, 409 |

> Requests also work through the API Gateway at `http://localhost:8080/api/vehicles...`
> (routes to `VEHICLE-SERVICE` via Eureka discovery).

### Register a vehicle

```
POST http://localhost:8082/api/vehicles
Content-Type: application/json
```

Request:

```json
{
  "userId": 1,
  "vehicleNumber": "ABC-1234",
  "vehicleType": "CAR",
  "brand": "Toyota",
  "model": "Corolla"
}
```

Response `201 Created` (new vehicles always start `OUTSIDE`):

```json
{
  "id": 1,
  "userId": 1,
  "vehicleNumber": "ABC-1234",
  "vehicleType": "CAR",
  "brand": "Toyota",
  "model": "Corolla",
  "status": "OUTSIDE",
  "entryTime": null,
  "exitTime": null,
  "createdAt": "2026-08-14T16:51:05.497583Z",
  "updatedAt": "2026-08-14T16:51:05.497583Z"
}
```

Validation: `userId` required (positive); `vehicleNumber` required (3-15
letters/digits/spaces/hyphens, normalized to upper case) and unique; `vehicleType`
required (one of `CAR | SUV | VAN | TRUCK | MOTORCYCLE | BUS`); `brand` and `model`
required.

### Simulated entry / exit

```
POST http://localhost:8082/api/vehicles/1/entry
POST http://localhost:8082/api/vehicles/1/exit
```

Entry → `200` with `"status": "INSIDE"` and `entryTime` set (`exitTime` cleared).
Exit → `200` with `"status": "OUTSIDE"` and `exitTime` set (`entryTime` cleared).

| Attempt                              | Result        |
|--------------------------------------|---------------|
| Entry on a vehicle already `INSIDE`  | 409 Conflict  |
| Exit on a vehicle that is `OUTSIDE`  | 409 Conflict  |
| Entry/exit on an unknown id          | 404 Not Found |

The status invariant is always `INSIDE  -> entryTime set,  exitTime null` and
`OUTSIDE -> exitTime  set,  entryTime null`, so the response is self-consistent.

## Error handling

Errors use the same JSON envelope as the User Service and API Gateway:

```json
{
  "timestamp": "2026-08-14T16:51:14.253399Z",
  "status": 409,
  "error": "Conflict",
  "message": "Vehicle is already inside: ABC-1234",
  "path": "/api/vehicles/1/entry",
  "fieldErrors": null
}
```

| Situation                    | Status code |
|------------------------------|-------------|
| Vehicle not found            | 404         |
| Duplicate vehicle number     | 409         |
| Invalid entry/exit transition | 409         |
| Invalid input                | 400         |
| Unexpected error             | 500         |

## Database

Uses **PostgreSQL** - all SPMS services share the same database
(`smart_parking_db`). Connection settings come from the Config Server and can be
overridden via environment variables (the defaults match the local setup):

| Environment variable | Default            | Purpose                   |
|----------------------|--------------------|---------------------------|
| `DB_HOST`            | `localhost`        | PostgreSQL host           |
| `DB_PORT`            | `5432`             | PostgreSQL port           |
| `DB_NAME`            | `smart_parking_db` | Database name             |
| `DB_USERNAME`        | `postgres`         | DB user                   |
| `DB_PASSWORD`        | (your local postgres password) | DB password    |
| `VEHICLE_SERVICE_PORT`| `8082`            | Service port              |
| `EUREKA_SERVER_URL`  | `http://localhost:8761/eureka/` | Eureka registry URL |
| `CONFIG_SERVER_URL`  | `http://localhost:8888` | Config Server URL |

The connection URL is assembled by the Config Server as
`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`. The `vehicles`
table (created automatically via `ddl-auto=update`, or by hand per
[`docs/database-setup.md`](../docs/database-setup.md)):

```
vehicles(id BIGSERIAL PK, user_id BIGINT NOT NULL, vehicle_number VARCHAR(20) UNIQUE,
         vehicle_type VARCHAR(20), brand VARCHAR, model VARCHAR, status VARCHAR(20),
         entry_time TIMESTAMPTZ, exit_time TIMESTAMPTZ,
         created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ)
```

> Dev convenience: the jar also ships with H2 on the runtime classpath so the
> service can be started without PostgreSQL for local smoke tests (see below).
> **PostgreSQL remains the configured database** — H2 is only used when explicitly
> requested via `--spring.datasource.*` overrides.

## Running

Build the whole project first (or just this module):

```bash
mvnw.cmd clean install
# or, from the project root:  mvnw.cmd -pl vehicle-service clean install
```

Start the infrastructure (each in its own terminal), then the service:

```bash
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar
java -jar vehicle-service/target/vehicle-service-0.0.1-SNAPSHOT.jar
```

The service registers with Eureka as `VEHICLE-SERVICE` on port `8082` and pulls
its datasource/JPA configuration from the Config Server. Confirm with:

| Check                          | URL / Command                              |
|--------------------------------|--------------------------------------------|
| Health (actuator)              | http://localhost:8082/actuator/health      |
| Registered in Eureka           | http://localhost:8761/eureka/apps          |
| Config delivered by Config Svr | http://localhost:8888/vehicle-service/default |

### Running without PostgreSQL (H2 fallback)

If no PostgreSQL is available, override the datasource on the command line:

```bash
java -jar vehicle-service/target/vehicle-service-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url="jdbc:h2:mem:vehicles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa \
  --spring.datasource.password= \
  --spring.jpa.hibernate.ddl-auto=create-drop
```

## Tests

Integration tests (`src/test/java/.../VehicleServiceApplicationTests.java`) boot
the service on a random port with an in-memory H2 database and exercise every
endpoint plus the entry/exit state machine (23 tests):

```bash
mvnw.cmd -pl vehicle-service test
```

- registration 201, duplicate number → 409, missing/invalid fields → 400,
  invalid vehicle type → 400
- get by id 200 / 404, list by user (empty + non-empty), update 200 / 409 / 404,
  delete 204 / 404
- entry marks `INSIDE` + records entry time, double-entry → 409
- exit marks `OUTSIDE` + records exit time, exit-when-outside → 409
- full entry → exit → entry cycle, entry/exit on unknown vehicle → 404

## Package layout

```
src/main/java/com/smartparkingmanagementsystem/vehicle/
├── VehicleServiceApplication.java     # Spring Boot bootstrap
├── controller/VehicleController.java  # REST endpoints
├── dto/                               # request/response records
├── exception/                         # custom exceptions + global handler + ApiError
├── model/Vehicle.java, VehicleStatus.java, VehicleType.java
├── repository/VehicleRepository.java  # Spring Data JPA repository
└── service/VehicleService.java        # business logic + entry/exit state machine
```
