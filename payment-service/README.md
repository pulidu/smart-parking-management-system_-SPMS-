# payment-service

A **mock payment gateway** for the Smart Parking Management System (SPMS): it
validates mock card data, processes parking payments, simulates
`SUCCESS`/`FAILED` transactions, stores transactions, generates digital receipts
and prevents duplicate payments for the same reservation.

**Status: implemented (Phase 5).** Spring Boot 4.1.0 WebMVC service backed by
PostgreSQL, registered with Eureka as `PAYMENT-SERVICE`, consuming its
centralized configuration from the Config Server. The API Gateway already routes
`/api/payments/**` to it via `lb://PAYMENT-SERVICE`.

> **Mock only.** No real Stripe / PayPal / Visa / other external payment provider
> is integrated. Card validation is a mock "basic format" check (13-19 digits +
> Luhn) and the gateway outcome is deterministic (see
> [Mock simulation](#mock-simulation)).

## Responsibilities

- Validate mock payment/card data (400 on invalid data)
- Process a parking payment for a reservation
- Simulate `SUCCESS` / `FAILED` transactions
- Store the payment transaction (full card number is **never** stored)
- Generate a digital receipt
- Retrieve payment status / history
- Prevent duplicate payment for the same reservation (409)

## API endpoints

All endpoints live under `/api/payments/...`. Requests/responses are JSON.

| Method | Path                                         | Description                   | Success | Errors |
|--------|----------------------------------------------|-------------------------------|---------|--------|
| POST   | `/api/payments`                              | Process a mock payment        | 201     | 400, 404, 409, 503 |
| GET    | `/api/payments/{id}`                         | Get a payment (incl. status)  | 200     | 404    |
| GET    | `/api/payments/reservation/{reservationId}`  | List a reservation's payments | 200     | -      |
| GET    | `/api/payments/user/{userId}`                | List a user's payments        | 200     | -      |
| GET    | `/api/payments/{id}/receipt`                 | Get the digital receipt       | 200     | 404    |

Payment status values: `PENDING`, `SUCCESS`, `FAILED`.
Payment method values: `CARD`, `CASH`, `MOCK_WALLET`.

> Requests also work through the API Gateway at `http://localhost:8080/api/payments...`.

### Process a payment

```
POST http://localhost:8084/api/payments
Content-Type: application/json
```

Request:

```json
{
  "reservationId": 100,
  "userId": 1,
  "amount": 500,
  "paymentMethod": "CARD",
  "cardNumber": "4111111111111111"
}
```

Response `201 Created` — the transaction is **always stored**, so the HTTP
response is `201` and the outcome is described by the `status` field:

```json
{
  "id": 1,
  "reservationId": 100,
  "userId": 1,
  "amount": 500.00,
  "paymentMethod": "CARD",
  "transactionId": "TXN-1786739274528-220274",
  "status": "SUCCESS",
  "paymentDate": "2026-08-14T20:27:54.528771Z",
  "createdAt": "2026-08-14T20:27:54.639257Z",
  "maskedCardNumber": "**** **** **** 1111"
}
```

A **failed** transaction returns `201` with `"status": "FAILED"` (still stored
for audit; retrying the same reservation afterwards is allowed). For `CASH` /
`MOCK_WALLET` no card is needed and `maskedCardNumber` is `null`.

### Mock card validation

| Rule                                                      | Failure |
|-----------------------------------------------------------|---------|
| `cardNumber` is required for `CARD` payments              | 400     |
| `cardNumber` must be 13-19 digits                         | 400     |
| `cardNumber` must pass the **Luhn** checksum (basic format)| 400     |
| `reservationId`, `userId` required and positive            | 400     |
| `amount` required and at least `0.01`                      | 400     |
| `paymentMethod` must be `CARD` / `CASH` / `MOCK_WALLET`    | 400     |

The full card number is used only for validation and masking; the database
stores just the **last four digits** (`card_last4`) and responses return a
masked value such as `**** **** **** 1111`.

### Mock simulation

The gateway settles deterministically so behaviour is reproducible:

- `CARD` payments **succeed** unless the card number equals the configured
  mock-failed card (default `4000000000000002`, the well-known decline test
  number) → `FAILED`.
- `CASH` and `MOCK_WALLET` always succeed.

The mock-failed card is configurable via `MOCK_FAILED_CARD` (see
[Configuration](#configuration)).

### Payment processing logic (create)

1. Validate the payment/card data → else `400 Bad Request`.
2. Verify the reservation exists by asking the parking service
   (`GET /api/parking/reservations/{id}`) → `404 Not Found` if it does not,
   `503 Service Unavailable` if the parking service cannot be reached.
3. Prevent a duplicate payment — if the reservation already has a
   `PENDING`/`SUCCESS` payment → `409 Conflict`. A previously `FAILED` payment
   does **not** block a retry.
4. Generate a unique `transactionId` (`TXN-<timestamp>-<random>`).
5. Simulate the gateway → `SUCCESS` or `FAILED`.
6. Store the transaction and return the `201` response.

### Receipt

```
GET http://localhost:8084/api/payments/1/receipt
```

```json
{
  "receiptId": "RCPT-1",
  "transactionId": "TXN-1786739274528-220274",
  "reservationId": 100,
  "userId": 1,
  "amount": 500.00,
  "paymentMethod": "CARD",
  "paymentStatus": "SUCCESS",
  "paymentDate": "2026-08-14T20:27:54.528771Z"
}
```

`receiptId` is derived from the payment id (`RCPT-<paymentId>`) so it is stable
and reproducible.

### Reservation verification

Payment creation verifies the reservation against the **parking service** via a
`ReservationVerifier` (`RestClient`). The parking service is reached through
**Eureka service discovery** using the load-balanced name `lb://PARKING-SERVICE`
— the instance address is resolved at runtime and no host/port is hardcoded.
This keeps the services decoupled at the database level (no foreign keys) while
still enforcing the `404` rule. It is toggled by
`payment-service.verify-reservation` (default `true`) so the payment service can
also run standalone for local smoke tests. The inter-service call has explicit
timeouts (`payment-service.client.connect-timeout-ms` / `.read-timeout-ms`,
default 3s/5s) so a slow or unreachable parking service cannot hang a payment.

## Error handling

Errors use the same JSON envelope as the other SPMS services:

```json
{
  "timestamp": "2026-08-14T20:28:02.440947Z",
  "status": 404,
  "error": "Not Found",
  "message": "Reservation not found with id: 99999",
  "path": "/api/payments",
  "fieldErrors": null
}
```

| Situation                                      | Status code |
|------------------------------------------------|-------------|
| Payment / reservation not found                | 404         |
| Duplicate payment for the same reservation     | 409         |
| Invalid payment data (card format / Luhn / required for CARD) | 400 |
| Parking service unreachable while verifying    | 503         |
| Invalid input / malformed body                 | 400         |
| Unexpected error                               | 500         |

## Database

Uses **PostgreSQL** - all SPMS services share the same database
(`smart_parking_db`). Connection settings come from the Config Server and can be
overridden via environment variables (the defaults match the local setup):

| Environment variable    | Default            | Purpose                        |
|-------------------------|--------------------|--------------------------------|
| `DB_HOST`               | `localhost`        | PostgreSQL host                |
| `DB_PORT`               | `5432`             | PostgreSQL port                |
| `DB_NAME`               | `smart_parking_db` | Database name                  |
| `DB_USERNAME`           | `postgres`         | DB user                        |
| `DB_PASSWORD`           | (your local postgres password) | DB password          |
| `PAYMENT_SERVICE_PORT`  | `8084`             | Service port                   |
| `EUREKA_SERVER_URL`     | `http://localhost:8761/eureka/` | Eureka registry URL |
| `CONFIG_SERVER_URL`     | `http://localhost:8888` | Config Server URL      |
| `MOCK_FAILED_CARD`      | `4000000000000002` | Card that the mock gateway declines |

The parking service is **not** configured via URL - the `ReservationVerifier`
resolves it through Eureka as `lb://PARKING-SERVICE` at runtime.

The connection URL is assembled by the Config Server as
`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`. Tables are created
automatically via `ddl-auto=update` (or by hand per
[`docs/database-setup.md`](../docs/database-setup.md)):

```
payments(id BIGSERIAL PK, reservation_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
         amount NUMERIC(10,2) NOT NULL, payment_method VARCHAR(20) NOT NULL,
         transaction_id VARCHAR(50) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL,
         payment_date TIMESTAMPTZ NOT NULL, card_last4 VARCHAR(4),
         created_at TIMESTAMPTZ NOT NULL)
```

The `reservation_id` and `user_id` columns are plain references (no foreign
keys) so the service stays decoupled from the parking/user services. The
duplicate-payment guard runs in the service layer; in PostgreSQL a partial
unique index (`UNIQUE ... ON payments(reservation_id) WHERE status = 'SUCCESS'`)
is documented as the race-condition safety net.

> Dev convenience: the jar also ships with H2 on the runtime classpath so the
> service can be started without PostgreSQL for local smoke tests (see below).
> **PostgreSQL remains the configured database** — H2 is only used when
> explicitly requested via `--spring.datasource.*` overrides.

## Running

Build the whole project first (or just this module):

```bash
mvnw.cmd clean install
# or, from the project root:  mvnw.cmd -pl payment-service clean install
```

Start the infrastructure (each in its own terminal), then the services:

```bash
java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar
java -jar parking-service/target/parking-service-0.0.1-SNAPSHOT.jar   # needed to verify reservations
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
```

The service registers with Eureka as `PAYMENT-SERVICE` on port `8084` and pulls
its datasource/JPA configuration from the Config Server. Confirm with:

| Check                          | URL / Command                              |
|--------------------------------|--------------------------------------------|
| Health (actuator)              | http://localhost:8084/actuator/health      |
| Registered in Eureka           | http://localhost:8761/eureka/apps          |
| Config delivered by Config Svr | http://localhost:8888/payment-service/default |

### Running without PostgreSQL (H2 fallback)

```bash
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url="jdbc:h2:mem:payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa \
  --spring.datasource.password= \
  --spring.jpa.hibernate.ddl-auto=create-drop \
  --payment-service.verify-reservation=false   # skip the parking-service check for standalone smoke tests
```

## Tests

Integration tests (`src/test/java/.../PaymentServiceApplicationTests.java`) boot
the service on a random port with an in-memory H2 database and a mocked
`ReservationVerifier`, and exercise every endpoint plus the mock gateway rules
(27 tests):

```bash
mvnw.cmd -pl payment-service test
```

- process payment: CARD 201 SUCCESS with masked card, CASH 201, MOCK_WALLET 201,
  mock-failed card 201 FAILED, missing cardNumber for CARD 400, bad format 400,
  non-numeric 400, Luhn failure 400, missing/zero amount 400, invalid method 400,
  missing userId/reservationId 400
- reservation verification: reservation not found 404 (mocked verifier)
- duplicate guard: duplicate payment for the same reservation 409, retry allowed
  after FAILED, distinct reservations both payable
- retrieval: get 200/404, list by reservation (full + empty), list by user
  (full + empty)
- receipts: receipt 200 with all fields, receipt not found 404
- generated transaction ids are unique across payments

## Package layout

```
src/main/java/com/smartparkingmanagementsystem/payment/
├── PaymentServiceApplication.java       # Spring Boot bootstrap
├── config/RestClientConfig.java         # @LoadBalanced RestClient.Builder + timeouts
├── controller/PaymentController.java        # /api/payments**
├── dto/                                # request/response/receipt records
├── exception/                          # custom exceptions + global handler + ApiError
├── model/Payment.java, PaymentStatus.java, PaymentMethod.java
├── repository/PaymentRepository.java
└── service/PaymentService.java, ReservationVerifier.java
```
