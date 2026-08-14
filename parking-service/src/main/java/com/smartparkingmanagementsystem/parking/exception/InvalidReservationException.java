package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when a reservation request is invalid (e.g. start time after end time).
 */
public class InvalidReservationException extends RuntimeException {

    public InvalidReservationException(String message) {
        super(message);
    }

}
