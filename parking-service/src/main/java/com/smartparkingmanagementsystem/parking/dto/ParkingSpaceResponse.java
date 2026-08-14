package com.smartparkingmanagementsystem.parking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.smartparkingmanagementsystem.parking.model.ParkingSpaceStatus;

/**
 * Public parking space representation.
 */
public record ParkingSpaceResponse(
        Long id,
        Long ownerId,
        String spaceNumber,
        String location,
        String city,
        String zone,
        BigDecimal pricePerHour,
        ParkingSpaceStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
