package com.smartparkingmanagementsystem.parking.exception;

/**
 * Thrown when creating/updating a parking space would duplicate an existing
 * space number owned by the same owner.
 */
public class DuplicateParkingSpaceException extends RuntimeException {

    public DuplicateParkingSpaceException(String message) {
        super(message);
    }

}
