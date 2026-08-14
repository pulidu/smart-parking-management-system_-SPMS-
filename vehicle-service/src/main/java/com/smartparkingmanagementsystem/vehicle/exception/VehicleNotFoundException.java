package com.smartparkingmanagementsystem.vehicle.exception;

/**
 * Thrown when a vehicle cannot be found by id.
 */
public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(String message) {
        super(message);
    }

}
