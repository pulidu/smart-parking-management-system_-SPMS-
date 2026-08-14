package com.smartparkingmanagementsystem.payment.exception;

/**
 * Thrown when the reservation already has an active (pending/success) payment
 * and another one is attempted - the duplicate-payment guard.
 */
public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(String message) {
        super(message);
    }

}
