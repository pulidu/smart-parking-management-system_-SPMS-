package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a cancel/release is attempted on a reservation that is no longer
 * active (already cancelled or completed).
 */
public class ReservationStateException extends RuntimeException {

    public ReservationStateException(String message) {
        super(message);
    }

}
