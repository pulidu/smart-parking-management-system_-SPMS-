package com.smartparkingmanagementsystem.parking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /api/parking/spaces} (create a parking space).
 */
public record CreateParkingSpaceRequest(
        @NotNull(message = "ownerId is required")
        @Positive(message = "ownerId must be a positive number")
        Long ownerId,

        @NotBlank(message = "spaceNumber is required")
        @Pattern(regexp = "^[A-Z0-9\\s-]{1,20}$",
                message = "spaceNumber must be 1-20 letters, digits, spaces or hyphens")
        String spaceNumber,

        @NotBlank(message = "location is required")
        String location,

        @NotBlank(message = "city is required")
        String city,

        @NotBlank(message = "zone is required")
        String zone,

        @NotNull(message = "pricePerHour is required")
        @DecimalMin(value = "0.01", message = "pricePerHour must be at least 0.01")
        BigDecimal pricePerHour) {
}
