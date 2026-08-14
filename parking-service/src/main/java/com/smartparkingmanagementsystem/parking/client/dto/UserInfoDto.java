package com.smartparkingmanagementsystem.parking.client.dto;

/**
 * User representation consumed from the User Service over REST. A plain record
 * so the parking service never depends on the User Service's internal model
 * classes (clean-DTO boundary between services).
 */
public record UserInfoDto(
        Long id,
        String name,
        String email,
        String phone,
        String role) {
}
