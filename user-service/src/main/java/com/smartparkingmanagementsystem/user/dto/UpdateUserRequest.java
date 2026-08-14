package com.smartparkingmanagementsystem.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /api/users/{id}} (profile update).
 * Only the fields present in this payload can be changed; {@code password} is
 * optional and re-hashed only when supplied.
 */
public record UpdateUserRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @Pattern(regexp = "^\\+?[0-9()\\-\\s]{7,20}$",
                message = "phone must be a valid phone number")
        String phone,

        @Size(min = 6, message = "password must be at least 6 characters")
        String password) {
}
