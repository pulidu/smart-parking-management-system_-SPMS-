package com.smartparkingmanagementsystem.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Bootstrap class for the Eureka Service Registry.
 *
 * <p>Running this application starts a standalone Netflix Eureka Server on
 * port 8761. Every other SPMS microservice registers with this registry so
 * that services can discover each other by logical name instead of by
 * hard-coded network address.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }

}
