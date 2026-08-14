package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when an operation requires the parking space to be in a certain state
 * (e.g. AVAILABLE to be reserved) and it is not.
 */
public class ParkingSpaceStateException extends RuntimeException {

    public ParkingSpaceStateException(String message) {
        super(message);
    }

}
