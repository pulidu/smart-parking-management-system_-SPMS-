package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a parking space already has an active (pending/confirmed)
 * reservation and a new one is attempted - the double-reservation guard.
 */
public class DuplicateReservationException extends RuntimeException {

    public DuplicateReservationException(String message) {
        super(message);
    }

}
