# Postman Testing Guide — Smart Parking Management System

Complete end-to-end REST testing flow for the microservices stack, driven through
the API Gateway (`http://localhost:8080`). All request/response shapes below were
taken directly from the source (`@RequestMapping` mappings, DTO records, service
business rules and the `GlobalExceptionHandler`s) — nothing is guessed.

## 0. Prerequisites

- All 6 Spring Boot services registered and UP in Eureka (`http://localhost:8761`):
  `eureka-server`, `config-server`, `api-gateway`, `user-service`, `vehicle-service`,
  `parking-service`, `payment-service`.
- PostgreSQL running, database `smart_parking_db` created (see `docs/database-setup.md`).
- Use **Postman** with the collection structure in section 7.

### Base URLs

| Target          | URL                       |
|-----------------|---------------------------|
| API Gateway     | `http://localhost:8080`   |
| User Service    | `http://localhost:8081`   |
| Vehicle Service | `http://localhost:8082`   |
| Parking Service | `http://localhost:8083`   |
| Payment Service | `http://localhost:8084`   |

All examples below use the **Gateway URL** `http://localhost:8080`.

### Gateway route map

| Gateway route           | Forwarded to (Eureka)  |
|-------------------------|------------------------|
| `/api/users/**`         | `lb://USER-SERVICE`    |
| `/api/vehicles/**`      | `lb://VEHICLE-SERVICE` |
| `/api/parking/**`       | `lb://PARKING-SERVICE` |
| `/api/payments/**`      | `lb://PAYMENT-SERVICE` |

Verify the live routes: `GET http://localhost:8080/actuator/gateway/routes`

---

## 1. Enums / allowed values

| Enum                | Values                                                          |
|---------------------|-----------------------------------------------------------------|
| `Role`              | `DRIVER`, `OWNER`, `ADMIN`                                       |
| `VehicleType`       | `CAR`, `SUV`, `VAN`, `TRUCK`, `MOTORCYCLE`, `BUS`               |
| `VehicleStatus`     | `OUTSIDE`, `INSIDE` (server-managed)                             |
| `ParkingSpaceStatus`| `AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`              |
| `ReservationStatus` | `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`                |
| `PaymentMethod`     | `CARD`, `CASH`, `MOCK_WALLET`                                   |
| `PaymentStatus`     | `PENDING`, `SUCCESS`, `FAILED` (mock gateway settles synchronously) |

### Uniform error body (all services)

```json
{
  "timestamp": "2026-08-15T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "A payment already exists for reservation 5",
  "path": "/api/payments",
  "fieldErrors": null
}
```

Validation failures return `400` with `message: "Validation failed"` and
`fieldErrors: { "<field>": "<message>", ... }`.

---

## 2. Endpoint reference

### 2.1 User Service — base `/api/users`

| Method | Path                | Body / Params                    | Success | Error codes |
|--------|---------------------|----------------------------------|---------|-------------|
| POST   | `/api/users`        | `CreateUserRequest`              | `201` `UserResponse` | `400` (validation), `409` (duplicate email) |
| POST   | `/api/users/login`  | `LoginRequest`                   | `200` `LoginResponse` | `400`, `401` (bad credentials) |
| GET    | `/api/users/{id}`   | –                                | `200` `UserResponse` | `404` |
| PUT    | `/api/users/{id}`   | `UpdateUserRequest`              | `200` `UserResponse` | `400`, `404`, `409` (email taken) |
| GET    | `/api/users/{id}/bookings` | –                          | `200` `UserBookingsResponse` (always empty placeholder) | `404` |

**`CreateUserRequest`:** `name` (required), `email` (valid email, lowercased by
service), `password` (min 6), `phone` (optional, pattern `^\+?[0-9()\-\s]{7,20}$`),
`role` (required).

**`UpdateUserRequest`:** `name`, `email` required; `phone` optional; `password`
optional (re-hashed only when supplied).

**`UserResponse`:** `id, name, email, phone, role, createdAt, updatedAt`
(never exposes the password hash).

**`LoginResponse`:** `{ "token": null, "user": { ...UserResponse } }` — token is
reserved for a future JWT layer and is always `null`.

### 2.2 Vehicle Service — base `/api/vehicles`

| Method | Path                     | Body / Params          | Success | Error codes |
|--------|--------------------------|------------------------|---------|-------------|
| POST   | `/api/vehicles`          | `CreateVehicleRequest` | `201` `VehicleResponse` | `400`, `409` (duplicate number) |
| GET    | `/api/vehicles/{id}`     | –                      | `200` `VehicleResponse` | `404` |
| GET    | `/api/vehicles/user/{userId}` | –                  | `200` `[VehicleResponse]` | – |
| PUT    | `/api/vehicles/{id}`     | `UpdateVehicleRequest` | `200` `VehicleResponse` | `400`, `404`, `409` |
| DELETE | `/api/vehicles/{id}`     | –                      | `204` (no body) | `404` |
| POST   | `/api/vehicles/{id}/entry` | –                    | `200` `VehicleResponse` (status `INSIDE`, `entryTime` set) | `404`, `409` (already inside) |
| POST   | `/api/vehicles/{id}/exit`  | –                    | `200` `VehicleResponse` (status `OUTSIDE`, `exitTime` set) | `404`, `409` (not inside) |

**`CreateVehicleRequest`:** `userId` (required, positive), `vehicleNumber`
(required, pattern `^[A-Z0-9\s-]{3,15}$`, normalized to uppercase), `vehicleType`
(required enum), `brand` (required), `model` (required).

**`UpdateVehicleRequest`:** same fields minus `userId`. Owner and status are not
changeable.

**`VehicleResponse`:** `id, userId, vehicleNumber, vehicleType, brand, model,
status, entryTime, exitTime, createdAt, updatedAt`. New vehicles start `OUTSIDE`.

> Note: vehicle registration does **not** validate that `userId` exists in the
> user-service. The ownership check happens later at reservation time.

### 2.3 Parking Service — base `/api/parking`

#### Parking spaces — `/api/parking/spaces`

| Method | Path                            | Body / Params | Success | Error codes |
|--------|---------------------------------|---------------|---------|-------------|
| POST   | `/api/parking/spaces`           | `CreateParkingSpaceRequest` | `201` `ParkingSpaceResponse` | `400`, `409` (duplicate number for owner) |
| GET    | `/api/parking/spaces`           | query: `city`, `zone`, `available` | `200` `[ParkingSpaceResponse]` | – |
| GET    | `/api/parking/spaces/{id}`      | –             | `200` `ParkingSpaceResponse` | `404` |
| PUT    | `/api/parking/spaces/{id}`      | `UpdateParkingSpaceRequest` | `200` `ParkingSpaceResponse` | `400`, `404`, `409` |
| DELETE | `/api/parking/spaces/{id}`      | –             | `204` | `404`, `409` (has active reservation) |
| PUT    | `/api/parking/spaces/{id}/status` | `UpdateStatusRequest` | `200` `ParkingSpaceResponse` | `400`, `404` |

**Search filters:** `available=true` → only `AVAILABLE`; `available=false` →
`RESERVED` + `OCCUPIED` + `MAINTENANCE`; omitted → all statuses. Example:
`GET /api/parking/spaces?city=Colombo&available=true`.

**`CreateParkingSpaceRequest`:** `ownerId` (required, positive), `spaceNumber`
(required, pattern `^[A-Z0-9\s-]{1,20}$`, uppercased), `location` (required),
`city` (required), `zone` (required), `pricePerHour` (required, ≥ `0.01`).

**`UpdateParkingSpaceRequest`:** same fields minus `ownerId`.

**`UpdateStatusRequest`:** `{ "status": "OCCUPIED" }` — free-form transition,
used to simulate IoT sensor updates.

**`ParkingSpaceResponse`:** `id, ownerId, spaceNumber, location, city, zone,
pricePerHour, status, createdAt, updatedAt`. New spaces start `AVAILABLE`.

#### Reservations — `/api/parking/reservations`

| Method | Path                                     | Body / Params | Success | Error codes |
|--------|------------------------------------------|---------------|---------|-------------|
| POST   | `/api/parking/reservations`              | `CreateReservationRequest` | `201` `ReservationResponse` | `400`, `404`, `409`, `503` |
| GET    | `/api/parking/reservations/{id}`         | –             | `200` `ReservationResponse` | `404` |
| GET    | `/api/parking/reservations/user/{userId}`| –             | `200` `[ReservationResponse]` | – |
| POST   | `/api/parking/reservations/{id}/cancel`  | –             | `200` `ReservationResponse` (`CANCELLED`, space freed) | `404`, `409` |
| POST   | `/api/parking/reservations/{id}/release` | –             | `200` `ReservationResponse` (`COMPLETED`, space freed) | `404`, `409` |

**`CreateReservationRequest`:** `userId`, `vehicleId`, `parkingSpaceId` (all
required, positive), `startTime`, `endTime` (both required `Instant`, ISO-8601;
`startTime` must be before `endTime`).

**Creation rules (in order):**
1. `startTime < endTime` else `400`.
2. User must exist in user-service, else `404`.
3. Vehicle must exist **and belong to that user**, else `404` / `409`.
4. User/Vehicle service unreachable → `503`.
5. Space must exist (`404`), be `AVAILABLE` (`409` otherwise).
6. No active (`PENDING`/`CONFIRMED`) reservation on the space, else `409`.
7. On success: reservation saved as `PENDING` and space flips `AVAILABLE → RESERVED`.

**`ReservationResponse`:** `id, userId, vehicleId, parkingSpaceId, startTime,
endTime, status, createdAt`.

**cancel/release** only work on `PENDING`/`CONFIRMED` reservations (`409` state
error otherwise). Both free the space back to `AVAILABLE`.

### 2.4 Payment Service — base `/api/payments`

| Method | Path                            | Body / Params | Success | Error codes |
|--------|---------------------------------|---------------|---------|-------------|
| POST   | `/api/payments`                 | `CreatePaymentRequest` | `201` `PaymentResponse` | `400`, `404`, `409`, `503` |
| GET    | `/api/payments/{id}`            | –             | `200` `PaymentResponse` | `404` |
| GET    | `/api/payments/reservation/{reservationId}` | –    | `200` `[PaymentResponse]` | – |
| GET    | `/api/payments/user/{userId}`   | –             | `200` `[PaymentResponse]` | – |
| GET    | `/api/payments/{id}/receipt`    | –             | `200` `ReceiptResponse` | `404` |

**`CreatePaymentRequest`:** `reservationId` (required, positive), `userId`
(required, positive), `amount` (required, ≥ `0.01`), `paymentMethod` (required
enum), `cardNumber` (optional; required for `CARD`).

**Payment rules:**
1. `CARD` requires `cardNumber`; when present it must be 13–19 digits **and pass
   the Luhn check** (`400` otherwise).
2. The reservation must exist in parking-service (`404`); parking unreachable →
   `503`.
3. A payment for the same reservation already `PENDING` or `SUCCESS` → `409`
   (retry allowed after `FAILED`).
4. Mock gateway: `CARD` with card `4000000000000002` → `FAILED`; everything else
   → `SUCCESS`. The transaction is **always stored** and the response is always
   `201` — the `status` field describes the outcome.
5. Full card number is never stored — only `card_last4`; responses return a
   masked value (`**** **** **** 1111`).

**`PaymentResponse`:** `id, reservationId, userId, amount, paymentMethod,
transactionId, status, paymentDate, createdAt, maskedCardNumber`.

**`ReceiptResponse`:** `receiptId` (`RCPT-<paymentId>`), `transactionId`,
`reservationId`, `userId`, `amount`, `paymentMethod`, `paymentStatus`,
`paymentDate`.

**Mock card numbers (Luhn-valid):**

| Card               | Result at `POST /api/payments`        |
|--------------------|---------------------------------------|
| `4111111111111111` | `201`, status `SUCCESS`               |
| `4000000000000002` | `201`, status `FAILED` (fail card)    |

---

## 3. Required business flow (happy path, 17 steps)

Postman variables used (see section 7): `baseUrl`, `userId`, `ownerId`,
`vehicleId`, `parkingSpaceId`, `reservationId`, `paymentId`.

### Step 1 — Register driver
`POST http://localhost:8080/api/users`
```json
{
  "name": "John Driver",
  "email": "john.driver@example.com",
  "password": "secret123",
  "phone": "+94771234567",
  "role": "DRIVER"
}
```
Expect `201`. Capture `id` → `userId`.

### Step 2 — Register owner
`POST http://localhost:8080/api/users`
```json
{
  "name": "Mary Owner",
  "email": "mary.owner@example.com",
  "password": "secret123",
  "phone": "+94779876543",
  "role": "OWNER"
}
```
Expect `201`. Capture `id` → `ownerId`.

### Step 3 — Login (verify credentials)
`POST http://localhost:8080/api/users/login`
```json
{ "email": "john.driver@example.com", "password": "secret123" }
```
Expect `200` with `user.id == userId`, `token == null`.

### Step 4 — Register vehicle
`POST http://localhost:8080/api/vehicles`
```json
{
  "userId": "{{userId}}",
  "vehicleNumber": "ABC-1234",
  "vehicleType": "CAR",
  "brand": "Toyota",
  "model": "Corolla"
}
```
Expect `201`, `status: "OUTSIDE"`. Capture `id` → `vehicleId`.

### Step 5 — Create parking space
`POST http://localhost:8080/api/parking/spaces`
```json
{
  "ownerId": "{{ownerId}}",
  "spaceNumber": "A-101",
  "location": "Level 1, Near Elevator",
  "city": "Colombo",
  "zone": "Zone A",
  "pricePerHour": 250.00
}
```
Expect `201`, `status: "AVAILABLE"`. Capture `id` → `parkingSpaceId`.

### Step 6 — Search parking spaces (availability filter)
`GET http://localhost:8080/api/parking/spaces?city=Colombo&available=true`
Expect `200` — the new space appears in the list with `status: "AVAILABLE"`.

### Step 7 — Create reservation
`POST http://localhost:8080/api/parking/reservations`
```json
{
  "userId": "{{userId}}",
  "vehicleId": "{{vehicleId}}",
  "parkingSpaceId": "{{parkingSpaceId}}",
  "startTime": "2026-08-20T08:00:00Z",
  "endTime": "2026-08-20T10:00:00Z"
}
```
Expect `201`, `status: "PENDING"`. Capture `id` → `reservationId`.

### Step 8 — Verify space became RESERVED
`GET http://localhost:8080/api/parking/spaces/{{parkingSpaceId}}`
Expect `200`, `status: "RESERVED"`.

### Step 9 — Get reservation by id
`GET http://localhost:8080/api/parking/reservations/{{reservationId}}`
Expect `200`, `status: "PENDING"`.

### Step 10 — Process payment (card, success)
`POST http://localhost:8080/api/payments`
```json
{
  "reservationId": "{{reservationId}}",
  "userId": "{{userId}}",
  "amount": 500.00,
  "paymentMethod": "CARD",
  "cardNumber": "4111111111111111"
}
```
Expect `201`, `status: "SUCCESS"`, `maskedCardNumber: "**** **** **** 1111"`.
Capture `id` → `paymentId`.

### Step 11 — Get receipt
`GET http://localhost:8080/api/payments/{{paymentId}}/receipt`
Expect `200` with `receiptId: "RCPT-{{paymentId}}"`, `transactionId` matching
step 10, `paymentStatus: "SUCCESS"`.

### Step 12 — List payments for reservation
`GET http://localhost:8080/api/payments/reservation/{{reservationId}}`
Expect `200` — array containing the step-10 payment.

### Step 13 — Vehicle entry (simulated)
`POST http://localhost:8080/api/vehicles/{{vehicleId}}/entry`
Expect `200`, `status: "INSIDE"`, `entryTime` populated.

### Step 14 — Vehicle exit (simulated)
`POST http://localhost:8080/api/vehicles/{{vehicleId}}/exit`
Expect `200`, `status: "OUTSIDE"`, `exitTime` populated, `entryTime: null`.

### Step 15 — Release reservation (parking finished)
`POST http://localhost:8080/api/parking/reservations/{{reservationId}}/release`
Expect `200`, `status: "COMPLETED"`.

### Step 16 — Verify space freed
`GET http://localhost:8080/api/parking/spaces/{{parkingSpaceId}}`
Expect `200`, `status: "AVAILABLE"`.

### Step 17 — List user bookings & vehicle history
`GET http://localhost:8080/api/users/{{userId}}/bookings`
Expect `200`, `bookings: []` (placeholder by design).
`GET http://localhost:8080/api/vehicles/user/{{userId}}`
Expect `200`, array with the registered vehicle.

### Optional — cancel flow
Create a second reservation on a fresh space, then:
`POST http://localhost:8080/api/parking/reservations/{{reservationId2}}/cancel`
Expect `200`, `status: "CANCELLED"`, and the space returns to `AVAILABLE`.

---

## 4. Alternative payment methods

```json
{ "reservationId": 5, "userId": 1, "amount": 500.00, "paymentMethod": "CASH" }
```
Expect `201`, `status: "SUCCESS"`, `maskedCardNumber: null`.

```json
{ "reservationId": 6, "userId": 1, "amount": 500.00, "paymentMethod": "MOCK_WALLET" }
```
Expect `201`, `status: "SUCCESS"`.

---

## 5. Negative test cases

| # | Request | Expect |
|---|---------|--------|
| N1 | `POST /api/users` with an existing email | `409` DuplicateEmail |
| N2 | `POST /api/users` with `"password": "abc"` | `400`, fieldErrors.password |
| N3 | `POST /api/users` with `"email": "not-an-email"` | `400`, fieldErrors.email |
| N4 | `POST /api/users` missing `role` | `400` |
| N5 | `POST /api/users/login` with wrong password | `401` |
| N6 | `GET /api/users/999999` | `404` |
| N7 | `PUT /api/users/{{userId}}` with another user's email | `409` |
| N8 | `POST /api/vehicles` with duplicate `vehicleNumber` | `409` |
| N9 | `POST /api/vehicles` with `vehicleNumber: "AB"` (too short) | `400` |
| N10 | `POST /api/vehicles` with `vehicleType: "HELICOPTER"` | `400` (malformed enum) |
| N11 | `GET /api/vehicles/999999` | `404` |
| N12 | `POST /api/vehicles/{{vehicleId}}/entry` twice | second call `409` |
| N13 | `POST /api/vehicles/{{vehicleId}}/exit` when already `OUTSIDE` | `409` |
| N14 | `POST /api/parking/spaces` duplicate `spaceNumber` for same `ownerId` | `409` |
| N15 | `POST /api/parking/spaces` with `pricePerHour: 0` | `400` |
| N16 | `PUT /api/parking/spaces/{{id}}/status` with `"status": "LOL"` | `400` |
| N17 | `DELETE /api/parking/spaces/{{parkingSpaceId}}` while it has an active reservation | `409` |
| N18 | `POST /api/parking/reservations` with `startTime >= endTime` | `400` |
| N19 | `POST /api/parking/reservations` with unknown `userId` | `404` (from user-service) |
| N20 | `POST /api/parking/reservations` with unknown `vehicleId` | `404` |
| N21 | `POST /api/parking/reservations` with a vehicle belonging to another user | `409` |
| N22 | `POST /api/parking/reservations` with unknown `parkingSpaceId` | `404` |
| N23 | `POST /api/parking/reservations` on a space that is not `AVAILABLE` | `409` |
| N24 | Second reservation on the same space while first is active | `409` |
| N25 | `POST /api/parking/reservations/{{id}}/cancel` after `release`/`cancel` | `409` state error |
| N26 | `POST /api/payments` with `paymentMethod: "CARD"`, no `cardNumber` | `400` |
| N27 | `POST /api/payments` with `cardNumber: "123"` (too short) | `400` |
| N28 | `POST /api/payments` with Luhn-invalid card `4111111111111112` | `400` |
| N29 | `POST /api/payments` with unknown `reservationId` | `404` |
| N30 | `POST /api/payments` twice for the same successful reservation | second call `409` |
| N31 | `POST /api/payments` with fail card `4000000000000002` | `201` + `status: "FAILED"` |
| N32 | `GET /api/payments/999999` or `/api/payments/999999/receipt` | `404` |

> `503` cases: stop the parking-service and call `POST /api/payments` → `503`;
> stop user-service (or vehicle-service) and call `POST /api/parking/reservations`
> → `503`.

---

## 6. Database verification

psql (adjust path):
```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d smart_parking_db
```

```sql
-- Users (Step 1/2)
SELECT id, name, email, phone, role FROM users ORDER BY id;

-- Vehicles (Step 4)
SELECT id, user_id, vehicle_number, vehicle_type, brand, model, status
FROM vehicles ORDER BY id;

-- Parking spaces (Step 5)
SELECT id, owner_id, space_number, location, city, zone, price_per_hour, status
FROM parking_spaces ORDER BY id;

-- Reservations (Step 7)
SELECT id, user_id, vehicle_id, parking_space_id,
       start_time, end_time, status
FROM reservations ORDER BY id;

-- Payments (Step 10/11)
SELECT id, reservation_id, user_id, amount, payment_method,
       transaction_id, status, card_last4
FROM payments ORDER BY id;

-- Full join across the business flow
SELECT r.id AS reservation_id, r.status AS reservation_status,
       p.id AS payment_id, p.status AS payment_status, p.transaction_id,
       s.space_number, s.status AS space_status,
       u.email AS driver, v.vehicle_number
FROM reservations r
JOIN parking_spaces s  ON s.id = r.parking_space_id
JOIN vehicles v        ON v.id = r.vehicle_id
JOIN users u           ON u.id = r.user_id
LEFT JOIN payments p   ON p.reservation_id = r.id
ORDER BY r.id;
```

---

## 7. Postman collection structure

### Collection-level variables

| Variable        | Initial value         |
|-----------------|-----------------------|
| `baseUrl`       | `http://localhost:8080` |
| `userId`        | *(empty)*             |
| `ownerId`       | *(empty)*             |
| `vehicleId`     | *(empty)*             |
| `parkingSpaceId`| *(empty)*             |
| `reservationId` | *(empty)*             |
| `paymentId`     | *(empty)*             |

### Folders & requests

```
Smart Parking Management System
├─ 1. Users
│   ├─ Register Driver      POST {{baseUrl}}/api/users
│   ├─ Register Owner       POST {{baseUrl}}/api/users
│   ├─ Login                POST {{baseUrl}}/api/users/login
│   ├─ Get User             GET  {{baseUrl}}/api/users/{{userId}}
│   ├─ Update User          PUT  {{baseUrl}}/api/users/{{userId}}
│   └─ Get User Bookings    GET  {{baseUrl}}/api/users/{{userId}}/bookings
├─ 2. Vehicles
│   ├─ Register Vehicle     POST {{baseUrl}}/api/vehicles
│   ├─ Get Vehicle          GET  {{baseUrl}}/api/vehicles/{{vehicleId}}
│   ├─ List User Vehicles   GET  {{baseUrl}}/api/vehicles/user/{{userId}}
│   ├─ Update Vehicle       PUT  {{baseUrl}}/api/vehicles/{{vehicleId}}
│   ├─ Delete Vehicle       DELETE {{baseUrl}}/api/vehicles/{{vehicleId}}
│   ├─ Vehicle Entry        POST {{baseUrl}}/api/vehicles/{{vehicleId}}/entry
│   └─ Vehicle Exit         POST {{baseUrl}}/api/vehicles/{{vehicleId}}/exit
├─ 3. Parking Spaces
│   ├─ Create Space         POST {{baseUrl}}/api/parking/spaces
│   ├─ Search Spaces        GET  {{baseUrl}}/api/parking/spaces?city=Colombo&available=true
│   ├─ Get Space            GET  {{baseUrl}}/api/parking/spaces/{{parkingSpaceId}}
│   ├─ Update Space         PUT  {{baseUrl}}/api/parking/spaces/{{parkingSpaceId}}
│   ├─ Update Space Status  PUT  {{baseUrl}}/api/parking/spaces/{{parkingSpaceId}}/status
│   └─ Delete Space         DELETE {{baseUrl}}/api/parking/spaces/{{parkingSpaceId}}
├─ 4. Reservations
│   ├─ Create Reservation   POST {{baseUrl}}/api/parking/reservations
│   ├─ Get Reservation      GET  {{baseUrl}}/api/parking/reservations/{{reservationId}}
│   ├─ List User Reservations GET {{baseUrl}}/api/parking/reservations/user/{{userId}}
│   ├─ Cancel Reservation   POST {{baseUrl}}/api/parking/reservations/{{reservationId}}/cancel
│   └─ Release Reservation  POST {{baseUrl}}/api/parking/reservations/{{reservationId}}/release
├─ 5. Payments
│   ├─ Create Payment       POST {{baseUrl}}/api/payments
│   ├─ Get Payment          GET  {{baseUrl}}/api/payments/{{paymentId}}
│   ├─ Payments by Reservation GET {{baseUrl}}/api/payments/reservation/{{reservationId}}
│   ├─ Payments by User     GET  {{baseUrl}}/api/payments/user/{{userId}}
│   └─ Get Receipt          GET  {{baseUrl}}/api/payments/{{paymentId}}/receipt
├─ 6. Negative Tests
│   └─ (N1–N32 above)
└─ 7. Infra
    ├─ Eureka Registry      GET  http://localhost:8761
    └─ Gateway Routes       GET  {{baseUrl}}/actuator/gateway/routes
```

### Chaining IDs with Postman scripts

Add this to the **Tests** tab of each creation request to chain IDs automatically:

```js
const json = pm.response.json();
if (json.id) {
  pm.collectionVariables.set(pm.variables.get("varName") || "userId", json.id);
}
```

Easier: set the target variable per request explicitly.

- **Register Driver** (Tests):
  ```js
  pm.collectionVariables.set("userId", pm.response.json().id);
  ```
- **Register Owner**:
  ```js
  pm.collectionVariables.set("ownerId", pm.response.json().id);
  ```
- **Register Vehicle**:
  ```js
  pm.collectionVariables.set("vehicleId", pm.response.json().id);
  ```
- **Create Space**:
  ```js
  pm.collectionVariables.set("parkingSpaceId", pm.response.json().id);
  ```
- **Create Reservation**:
  ```js
  pm.collectionVariables.set("reservationId", pm.response.json().id);
  ```
- **Create Payment**:
  ```js
  pm.collectionVariables.set("paymentId", pm.response.json().id);
  ```

### Smart timestamps for reservations

Use a **Pre-request Script** on "Create Reservation" so `startTime`/`endTime` are
always in the future:

```js
const start = new Date(Date.now() + 60 * 60 * 1000);   // now + 1h
const end   = new Date(start.getTime() + 2 * 60 * 60 * 1000); // + 2h more
pm.variables.set("resvStart", start.toISOString());
pm.variables.set("resvEnd", end.toISOString());
```
Body:
```json
{
  "userId": "{{userId}}",
  "vehicleId": "{{vehicleId}}",
  "parkingSpaceId": "{{parkingSpaceId}}",
  "startTime": "{{resvStart}}",
  "endTime": "{{resvEnd}}"
}
```
