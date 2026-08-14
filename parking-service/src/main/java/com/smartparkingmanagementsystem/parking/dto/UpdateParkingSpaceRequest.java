package com.smartparkingmanagementsystem.parking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code PUT /api/parking/spaces/{id}} (update a parking space).
 * The owner ({@code ownerId}) and status are managed by other operations and
 * cannot be changed here.
 */
public record UpdateParkingSpaceRequest(
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
