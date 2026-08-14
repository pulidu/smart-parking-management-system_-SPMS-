package com.smartparkingmanagementsystem.parking.client.dto;

/**
 * Vehicle representation consumed from the Vehicle Service over REST. A plain
 * record with String enums so the parking service never depends on the Vehicle
 * Service's internal model classes (clean-DTO boundary between services).
 */
public record VehicleInfoDto(
        Long id,
        Long userId,
        String vehicleNumber,
        String vehicleType,
        String brand,
        String model,
        String status) {
}
