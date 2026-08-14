package com.smartparkingmanagementsystem.payment.exception;

/**
 * Thrown when the parking service (used to verify that a reservation exists)
 * cannot be reached or returns an unexpected error.
 */
public class PaymentServiceUnavailableException extends RuntimeException {

    public PaymentServiceUnavailableException(String message) {
        super(message);
    }

}
