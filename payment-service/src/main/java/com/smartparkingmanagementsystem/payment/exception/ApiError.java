package com.smartparkingmanagementsystem.payment.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body returned by the global exception handler. The shape mirrors
 * the JSON produced by the API Gateway, User Service, Vehicle Service and
 * Parking Service error handlers so clients see a consistent error contract
 * across the whole system.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError withFieldErrors(int status, String error, String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }

}
