package com.smartparkingmanagementsystem.vehicle.exception;

/**
 * Thrown when an entry/exit transition is invalid for the vehicle's current
 * status (e.g. entering a vehicle that is already INSIDE, or exiting one that
 * is OUTSIDE).
 */
public class VehicleStateException extends RuntimeException {

    public VehicleStateException(String message) {
        super(message);
    }

}
