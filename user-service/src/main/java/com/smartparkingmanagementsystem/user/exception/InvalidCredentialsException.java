package com.smartparkingmanagementsystem.user.exception;

/**
 * Thrown when a login attempt fails (unknown email or wrong password).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

}
