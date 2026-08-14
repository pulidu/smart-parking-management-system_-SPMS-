package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a reservation references a vehicle that belongs to a different
 * user than the one requesting the reservation. Maps to 409.
 */
public class VehicleOwnershipException extends RuntimeException {

    public VehicleOwnershipException(String message) {
        super(message);
    }

}
