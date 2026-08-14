package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a parking space cannot be found by id.
 */
public class ParkingSpaceNotFoundException extends RuntimeException {

    public ParkingSpaceNotFoundException(String message) {
        super(message);
    }

}
