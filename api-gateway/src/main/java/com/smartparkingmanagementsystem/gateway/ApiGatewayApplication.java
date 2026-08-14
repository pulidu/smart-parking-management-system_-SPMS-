package com.smartparkingmanagementsystem.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap class for the Spring Cloud Gateway.
 *
 * <p>Running this application starts the API Gateway on port 8080. It is the single
 * REST entry point for the Smart Parking Management System: requests are routed to
 * backend microservices by Eureka service name (e.g. {@code lb://USER-SERVICE}) so
 * that no backend host/port is hard-coded in the gateway configuration.</p>
 *
 * <p>The gateway pulls its route configuration from the centralized Config Server
 * (see {@code spring.config.import} in {@code application.yml}), registers itself
 * with Eureka and fetches the registry to resolve service instances at runtime.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
