package com.smartparkingmanagementsystem.user.dto;

/**
 * Result of a successful login. {@code token} is reserved for a future JWT /
 * security layer and is currently {@code null}; the user profile is returned so
 * clients have everything they need until authentication is hardened.
 */
public record LoginResponse(
        String token,
        UserResponse user) {

    public static LoginResponse of(UserResponse user) {
        return new LoginResponse(null, user);
    }

}
