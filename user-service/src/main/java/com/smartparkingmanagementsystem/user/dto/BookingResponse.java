package com.smartparkingmanagementsystem.user.dto;

import java.time.Instant;

/**
 * Placeholder shape for a booking summary entry. The booking history feature is
 * implemented by the parking/booking services in a later phase; this DTO defines
 * the contract so the endpoint can be filled in without breaking clients.
 */
public record BookingResponse(
        Long id,
        String status,
        Instant createdAt) {
}
