package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a reservation references a user or vehicle that does not exist in
 * the owning microservice (User Service / Vehicle Service). Maps to 404.
 */
public class ReferencedResourceNotFoundException extends RuntimeException {

    public ReferencedResourceNotFoundException(String message) {
        super(message);
    }

}
