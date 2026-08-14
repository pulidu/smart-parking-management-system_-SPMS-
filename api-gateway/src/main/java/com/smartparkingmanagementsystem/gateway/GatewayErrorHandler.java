package com.smartparkingmanagementsystem.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Global error handler for the API Gateway.
 *
 * <p>Replaces the default error handler so that every failure surfaces as a clean
 * JSON document instead of a framework page. Typical cases handled:</p>
 * <ul>
 *     <li>no route matches the request path &rarr; 404</li>
 *     <li>a route matches but no service instance is available in Eureka &rarr;
 *     503 (the gateway throws a {@link ResponseStatusException} with status
 *     SERVICE_UNAVAILABLE for {@code lb://} targets that have no instances)</li>
 *     <li>any other failure &rarr; 500</li>
 * </ul>
 */
@Component
@Order(-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", exchange.getRequest().getPath().value());

        byte[] bytes = toJson(body, status);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private byte[] toJson(Map<String, Object> body, HttpStatus status) {
        try {
            return objectMapper.writeValueAsBytes(body);
        }
        catch (JsonProcessingException ex) {
            String fallback = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

}
