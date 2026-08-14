package com.smartparkingmanagementsystem.vehicle.model;

/**
 * Supported vehicle categories. Deserialized by the REST layer, so an unknown
 * value is rejected with a 400 before it ever reaches the service.
 */
public enum VehicleType {
    CAR,
    SUV,
    VAN,
    TRUCK,
    MOTORCYCLE,
    BUS
}
