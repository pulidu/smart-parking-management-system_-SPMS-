package com.smartparkingmanagementsystem.vehicle.exception;

/**
 * Thrown when a registration/update attempts to use a vehicle number that is
 * already registered.
 */
public class DuplicateVehicleNumberException extends RuntimeException {

    public DuplicateVehicleNumberException(String message) {
        super(message);
    }

}
