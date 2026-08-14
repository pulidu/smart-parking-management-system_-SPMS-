package com.smartparkingmanagementsystem.user.dto;

import java.time.Instant;

import com.smartparkingmanagementsystem.user.model.Role;

/**
 * Public user profile representation. Never exposes the password hash.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role,
        Instant createdAt,
        Instant updatedAt) {
}
