package com.smartparkingmanagementsystem.parking.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /api/parking/reservations}.
 * The start/end ordering (start before end) is validated in the service layer.
 */
public record CreateReservationRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be a positive number")
        Long userId,

        @NotNull(message = "vehicleId is required")
        @Positive(message = "vehicleId must be a positive number")
        Long vehicleId,

        @NotNull(message = "parkingSpaceId is required")
        @Positive(message = "parkingSpaceId must be a positive number")
        Long parkingSpaceId,

        @NotNull(message = "startTime is required")
        Instant startTime,

        @NotNull(message = "endTime is required")
        Instant endTime) {
}
