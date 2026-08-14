package com.smartparkingmanagementsystem.parking.model;

/**
 * Lifecycle of a parking reservation.
 *
 * <ul>
 *   <li>{@code PENDING} - created, waiting to be confirmed</li>
 *   <li>{@code CONFIRMED} - confirmed (reserved for future flows/payment)</li>
 *   <li>{@code CANCELLED} - cancelled before use</li>
 *   <li>{@code COMPLETED} - the space was used and released</li>
 * </ul>
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
