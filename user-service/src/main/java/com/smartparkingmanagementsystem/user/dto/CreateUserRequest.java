package com.smartparkingmanagementsystem.user.dto;

import com.smartparkingmanagementsystem.user.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/users} (user registration).
 */
public record CreateUserRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 6, message = "password must be at least 6 characters")
        String password,

        @Pattern(regexp = "^\\+?[0-9()\\-\\s]{7,20}$",
                message = "phone must be a valid phone number")
        String phone,

        @NotNull(message = "role is required")
        Role role) {
}
