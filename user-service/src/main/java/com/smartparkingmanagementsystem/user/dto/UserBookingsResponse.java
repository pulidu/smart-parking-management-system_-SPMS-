package com.smartparkingmanagementsystem.user.dto;

import java.util.List;

/**
 * Response envelope for {@code GET /api/users/{id}/bookings}. Currently a
 * placeholder: the list is always empty until the booking service exists.
 */
public record UserBookingsResponse(
        List<BookingResponse> bookings) {

    public static UserBookingsResponse empty() {
        return new UserBookingsResponse(List.of());
    }

}
