package com.smartparkingmanagementsystem.user.exception;

/**
 * Thrown when a user cannot be found by id or email.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

}
