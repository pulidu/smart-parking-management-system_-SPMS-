package com.smartparkingmanagementsystem.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Bootstrap class for the Spring Cloud Config Server.
 *
 * <p>Running this application starts a Config Server on port 8888 that serves
 * centralized configuration for every SPMS microservice from a local native
 * repository ({@code classpath:/config}). Clients fetch their configuration
 * through the REST endpoints {@code /{application}/{profile}}.</p>
 *
 * <p>The server also registers itself with the Eureka registry so that
 * microservices can discover it by the logical name {@code config-server}.</p>
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }

}
