package com.smartparkingmanagementsystem.parking.dto;

import com.smartparkingmanagementsystem.parking.model.ParkingSpaceStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Payload for {@code PUT /api/parking/spaces/{id}/status} - manual/IoT status
 * updates (e.g. {@code {"status": "OCCUPIED"}}).
 */
public record UpdateStatusRequest(
        @NotNull(message = "status is required")
        ParkingSpaceStatus status) {
}
