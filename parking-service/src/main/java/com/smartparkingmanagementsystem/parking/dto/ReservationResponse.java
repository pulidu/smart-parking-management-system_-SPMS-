package com.smartparkingmanagementsystem.parking.dto;

import java.time.Instant;

import com.smartparkingmanagementsystem.parking.model.ReservationStatus;

/**
 * Public reservation representation.
 */
public record ReservationResponse(
        Long id,
        Long userId,
        Long vehicleId,
        Long parkingSpaceId,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt) {
}
