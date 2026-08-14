package com.smartparkingmanagementsystem.user.exception;

/**
 * Thrown when a registration/update attempts to use an email that is already
 * registered.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }

}
