package com.smartparkingmanagementsystem.payment.exception;

/**
 * Thrown when a stored payment cannot be found by id.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }

}
