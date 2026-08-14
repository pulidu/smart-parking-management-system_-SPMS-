package com.smartparkingmanagementsystem.parking.model;

/**
 * Operational state of a parking space.
 *
 * <ul>
 *   <li>{@code AVAILABLE} - free to be reserved or entered directly</li>
 *   <li>{@code RESERVED} - held by an active (pending/confirmed) reservation</li>
 *   <li>{@code OCCUPIED} - a vehicle is currently parked (set manually / by IoT)</li>
 *   <li>{@code MAINTENANCE} - out of service</li>
 * </ul>
 */
public enum ParkingSpaceStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    MAINTENANCE
}
