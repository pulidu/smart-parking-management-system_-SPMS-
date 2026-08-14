# config-server

Spring Cloud Config Server — centralized external configuration for all SPMS
microservices. **Status: implemented (Phase 2).**

## Overview

The Config Server reads configuration from a **native (classpath) repository**
and serves it to clients via REST. It registers with the Eureka Service Registry
so that discovery-aware clients can look it up by its logical name `config-server`.

| Property                                   | Value                                                            |
|--------------------------------------------|------------------------------------------------------------------|
| Port                                       | `8888` (overridable via `CONFIG_SERVER_PORT`)                    |
| Profile                                    | `native`                                                         |
| Search location                            | `classpath:/config`                                              |
| Eureka registration                        | enabled — registers as `CONFIG-SERVER`                           |

## Repository contents

Config files live in `src/main/resources/config/`:

| File                 | Target service   | Key settings                                                        |
|----------------------|------------------|---------------------------------------------------------------------|
| `application.yml`    | all services     | shared Eureka client config + actuator exposure                     |
| `api-gateway.yml`    | `api-gateway`    | `server.port` (`${API_GATEWAY_PORT:8080}`)                          |
| `user-service.yml`   | `user-service`   | port + PostgreSQL datasource (env-var placeholders) + JPA (ddl-auto) |
| `vehicle-service.yml`| `vehicle-service`| port + PostgreSQL datasource (env-var placeholders) + JPA (ddl-auto) |
| `parking-service.yml`| `parking-service`| port + PostgreSQL datasource (env-var placeholders)                 |
| `payment-service.yml`| `payment-service`| port + PostgreSQL datasource (env-var placeholders)                 |

All secrets/credentials use environment-variable placeholders with localhost
defaults — no real secrets are committed. Example:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${USER_DB_NAME:user_db}
    username: ${USER_DB_USERNAME:user_service}
    password: ${USER_DB_PASSWORD:}
```

## Running

Build first from the project root, then run one of:

```bash
# Option A - from the project root, using the Maven wrapper
mvnw.cmd -pl config-server spring-boot:run

# Option B - run the built executable jar (after `mvnw.cmd clean install`)
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar

# Option C - from an IDE
# Open the project, then run com.smartparkingmanagementsystem.config.ConfigServerApplication
```

The Config Server expects Eureka at `http://localhost:8761/eureka/` (start the
Eureka Server first, see root README). Override with the `EUREKA_SERVER_URL`
environment variable if it runs elsewhere.

On a successful start you will see:

```
Tomcat started on port 8888 (http) with context path '/'
Started ConfigServerApplication ...
```

## Verifying

| Check                          | URL / Command                                                  |
|--------------------------------|----------------------------------------------------------------|
| Health (actuator)              | http://localhost:8888/actuator/health                          |
| Config for api-gateway         | http://localhost:8888/api-gateway/default                      |
| Config for user-service        | http://localhost:8888/user-service/default                     |
| Config for vehicle-service     | http://localhost:8888/vehicle-service/default                  |
| Config for parking-service     | http://localhost:8888/parking-service/default                  |
| Config for payment-service     | http://localhost:8888/payment-service/default                  |
| Shared config (all services)   | http://localhost:8888/application/default                      |
| Eureka registry (CONFIG-SERVER)| http://localhost:8761/eureka/apps                              |

The default `Accept` header returns YAML; `Accept: application/json` returns
JSON. Each response contains the service-specific property source plus the
shared `application.yml` source.

## Tests

`src/test/java/com/smartparkingmanagementsystem/config/ConfigServerApplicationTests.java`
boots the server on a random port (Eureka disabled) and asserts that every
service's `/{name}/default` endpoint returns the expected property sources.

```bash
mvnw.cmd -pl config-server test
```

## Configuration

Key settings in `src/main/resources/application.properties`:

```properties
server.port=${CONFIG_SERVER_PORT:8888}
spring.application.name=config-server
spring.profiles.active=native
spring.cloud.config.server.native.search-locations=classpath:/config
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```
