package com.smartparkingmanagementsystem.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "eureka.client.enabled=false", "spring.config.import=",
                "spring.cloud.config.import-check.enabled=false" })
class ApiGatewayApplicationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void registersExpectedRoutes() throws Exception {
        HttpResponse<String> response = fetch("/actuator/gateway/routes");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body)
                .contains("lb://USER-SERVICE")
                .contains("lb://VEHICLE-SERVICE")
                .contains("lb://PARKING-SERVICE")
                .contains("lb://PAYMENT-SERVICE")
                .contains("/api/users/**")
                .contains("/api/payments/**");
    }

    @Test
    void unmatchedPathReturns404() throws Exception {
        HttpResponse<String> response = fetch("/nope");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("404");
    }

    @Test
    void routedPathWithoutInstanceReturns503() throws Exception {
        HttpResponse<String> response = fetch("/api/users/1");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("503");
    }

    private HttpResponse<String> fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
