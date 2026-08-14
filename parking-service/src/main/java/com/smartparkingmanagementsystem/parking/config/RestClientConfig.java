package com.smartparkingmanagementsystem.parking.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides the load-balanced {@link RestClient} used by the parking service to
 * talk to other SPMS services. Callers use the {@code lb://EUREKA-SERVICE-ID}
 * scheme so the target instance address is resolved at runtime through Eureka
 * service discovery and round-robin load balancing instead of being hardcoded.
 *
 * <p>The client is exposed as a {@link RestClient} bean rather than a
 * {@code RestClient.Builder} bean: Spring Cloud's Eureka client auto-configures
 * its own HTTP transport from any {@code RestClient.Builder} bean present in the
 * context, and would otherwise inherit the load-balancer interceptor and try to
 * resolve the Eureka server host ({@code localhost}) as a service name.
 *
 * <p>Timeouts are applied so a slow or unreachable upstream service cannot hang
 * a reservation request indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient loadBalancedRestClient(
            DeferringLoadBalancerInterceptor loadBalancerInterceptor,
            @Value("${parking-service.client.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${parking-service.client.read-timeout-ms:5000}") long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(loadBalancerInterceptor)
                .build();
    }

}
