package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when an upstream microservice (User Service or Vehicle Service) cannot
 * be reached or returns an unexpected response while validating a reservation.
 * Maps to 503.
 */
public class UpstreamServiceUnavailableException extends RuntimeException {

    public UpstreamServiceUnavailableException(String message) {
        super(message);
    }

}
