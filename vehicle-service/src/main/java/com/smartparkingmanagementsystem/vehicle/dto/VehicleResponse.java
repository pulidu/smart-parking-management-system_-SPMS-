package com.smartparkingmanagementsystem.vehicle.dto;

import java.time.Instant;

import com.smartparkingmanagementsystem.vehicle.model.VehicleStatus;
import com.smartparkingmanagementsystem.vehicle.model.VehicleType;

/**
 * Public vehicle representation.
 */
public record VehicleResponse(
        Long id,
        Long userId,
        String vehicleNumber,
        VehicleType vehicleType,
        String brand,
        String model,
        VehicleStatus status,
        Instant entryTime,
        Instant exitTime,
        Instant createdAt,
        Instant updatedAt) {
}
