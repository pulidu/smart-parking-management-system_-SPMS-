package com.smartparkingmanagementsystem.vehicle.dto;

import com.smartparkingmanagementsystem.vehicle.model.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /api/vehicles} (vehicle registration).
 */
public record CreateVehicleRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be a positive number")
        Long userId,

        @NotBlank(message = "vehicleNumber is required")
        @Pattern(regexp = "^[A-Z0-9\\s-]{3,15}$",
                message = "vehicleNumber must be 3-15 letters, digits, spaces or hyphens")
        String vehicleNumber,

        @NotNull(message = "vehicleType is required")
        VehicleType vehicleType,

        @NotBlank(message = "brand is required")
        String brand,

        @NotBlank(message = "model is required")
        String model) {
}
