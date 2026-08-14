package com.smartparkingmanagementsystem.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class ConfigServerApplicationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void servesCommonApplicationConfiguration() throws Exception {
        String body = fetch("/application/default");
        assertThat(body)
                .contains("eureka")
                .contains("localhost:8761");
    }

    @Test
    void servesApiGatewayConfiguration() throws Exception {
        String body = fetch("/api-gateway/default");
        assertThat(body)
                .contains("api-gateway")
                .contains("8080")
                .contains("lb://USER-SERVICE")
                .contains("lb://VEHICLE-SERVICE")
                .contains("lb://PARKING-SERVICE")
                .contains("lb://PAYMENT-SERVICE")
                .contains("/api/users/**")
                .contains("/api/payments/**");
    }

    @Test
    void servesUserServiceConfiguration() throws Exception {
        String body = fetch("/user-service/default");
        assertThat(body)
                .contains("user-service")
                .contains("8081")
                .contains("jdbc:postgresql")
                .contains("ddl-auto")
                .contains("update");
    }

    @Test
    void servesVehicleServiceConfiguration() throws Exception {
        String body = fetch("/vehicle-service/default");
        assertThat(body)
                .contains("vehicle-service")
                .contains("8082")
                .contains("ddl-auto")
                .contains("update");
    }

    @Test
    void servesParkingServiceConfiguration() throws Exception {
        String body = fetch("/parking-service/default");
        assertThat(body)
                .contains("parking-service")
                .contains("8083");
    }

    @Test
    void servesPaymentServiceConfiguration() throws Exception {
        String body = fetch("/payment-service/default");
        assertThat(body)
                .contains("payment-service")
                .contains("8084");
    }

    private String fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

}
