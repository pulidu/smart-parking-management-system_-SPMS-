# api-gateway

Spring Cloud Gateway (WebFlux) — the single REST entry point for the Smart
Parking Management System. **Status: implemented (Phase 2).**

## Overview

The gateway exposes all backend microservices behind one port (`8080`) and
routes requests to them **by Eureka service name**, using Spring Cloud
LoadBalancer's `lb://` scheme. No backend host/port is hard-coded anywhere —
routes are resolved dynamically through Eureka service discovery.

It also consumes its own configuration (including the route table) from the
centralized Config Server.

| Property              | Value                                          |
|-----------------------|------------------------------------------------|
| Port                  | `8080` (overridable via `API_GATEWAY_PORT`)    |
| Stacks                | Spring WebFlux (reactive), Netty               |
| Discovery             | Eureka — registers as `API-GATEWAY`, fetches registry |
| Config Server         | pulled via `spring.config.import=optional:configserver:...` |

> **Gateway 5.x note:** this module uses the reactive starter
> `spring-cloud-starter-gateway-server-webflux`. In Spring Cloud Gateway 5.x the
> property prefix changed from `spring.cloud.gateway.*` to
> `spring.cloud.gateway.server.webflux.*`, so routes are declared under
> `spring.cloud.gateway.server.webflux.routes`.

## Route table

Routes are **centralized** in the Config Server, not in this module:
`config-server/src/main/resources/config/api-gateway.yml`.

| Route (request path) | Target (Eureka service ID) |
|----------------------|----------------------------|
| `/api/users/**`      | `lb://USER-SERVICE`        |
| `/api/vehicles/**`   | `lb://VEHICLE-SERVICE`     |
| `/api/parking/**`    | `lb://PARKING-SERVICE`     |
| `/api/payments/**`   | `lb://PAYMENT-SERVICE`     |

Example route definition (as served by the Config Server):

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: user-service
              uri: lb://USER-SERVICE
              predicates:
                - Path=/api/users/**
```

How a request flows through the gateway:

1. Client calls `GET http://localhost:8080/api/users/1`.
2. The `Path` predicate matches the `user-service` route.
3. The `lb://` URI triggers the reactive LoadBalancer filter, which resolves
   `USER-SERVICE` against the Eureka registry.
4. The request is forwarded to one of the registered `USER-SERVICE` instances
   (load-balanced); if none is registered the gateway returns `503`.

## Running

Build first from the project root, then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl api-gateway spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.gateway.ApiGatewayApplication
```

Start the Eureka Server first, then the Config Server (see root README). The
gateway expects Eureka at `http://localhost:8761/eureka/` and the Config Server
at `http://localhost:8888` — both overridable via the `EUREKA_SERVER_URL` and
`CONFIG_SERVER_URL` environment variables.

On a successful start you will see:

```
Netty started on port 8080 (http)
Started ApiGatewayApplication ...
```

## Verifying

| Check                        | URL / Command                                                    |
|------------------------------|------------------------------------------------------------------|
| Gateway health (actuator)    | http://localhost:8080/actuator/health                            |
| Active routes (actuator)     | http://localhost:8080/actuator/gateway/routes                    |
| Eureka registry (API-GATEWAY)| http://localhost:8761/eureka/apps                                |

### Example requests

All backend services are implemented, so these requests are routed end-to-end:

```bash
# Routed to USER-SERVICE
curl -i http://localhost:8080/api/users

# Routed to VEHICLE-SERVICE
curl -i http://localhost:8080/api/vehicles/1

# Routed to PARKING-SERVICE
curl -i http://localhost:8080/api/parking/spaces

# Routed to PAYMENT-SERVICE
curl -i http://localhost:8080/api/payments/1

# Unknown path - no route matches, gateway returns 404
curl -i http://localhost:8080/nope
```

## Global error handling

`GatewayErrorHandler` replaces the default error handler so every failure is a
clean JSON document:

| Case                                                       | Status |
|------------------------------------------------------------|--------|
| No route matches the request path                          | `404`  |
| Route matches but no instance registered in Eureka         | `503`  |
| Any other failure                                          | `500`  |

Response shape: `{"timestamp": "...", "status": 503, "error": "Service Unavailable", "message": "...", "path": "/api/users/1"}`.

## Configuration

Local bootstrap config in `src/main/resources/application.yml` (app name,
config-server import, Eureka, actuator). The route table intentionally lives in
the Config Server, so this file stays thin and can be re-pointed at any
Config Server via `CONFIG_SERVER_URL`.

## Tests

`src/test/java/com/smartparkingmanagementsystem/gateway/ApiGatewayApplicationTests.java`
boots the gateway on a random port with Eureka and the Config Server disabled
(test `application.yml` defines the same routes inline) and asserts:

- the 4 expected routes are registered (`/actuator/gateway/routes`);
- an unmatched path returns `404`;
- a matched route with no registered instance returns `503` (JSON error body).

```bash
mvnw.cmd -pl api-gateway test
```
