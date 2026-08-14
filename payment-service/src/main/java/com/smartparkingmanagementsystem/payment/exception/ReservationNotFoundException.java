package com.smartparkingmanagementsystem.payment.exception;

/**
 * Thrown when the parking service reports that the referenced reservation does
 * not exist (or the payment service cannot verify it).
 */
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String message) {
        super(message);
    }

}
