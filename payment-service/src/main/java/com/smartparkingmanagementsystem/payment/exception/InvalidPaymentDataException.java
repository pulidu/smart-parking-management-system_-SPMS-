package com.smartparkingmanagementsystem.payment.exception;

/**
 * Thrown when the payment payload is structurally valid but violates a business
 * rule (e.g. a card number is required for CARD payments, or the card number
 * fails the Luhn check).
 */
public class InvalidPaymentDataException extends RuntimeException {

    public InvalidPaymentDataException(String message) {
        super(message);
    }

}
