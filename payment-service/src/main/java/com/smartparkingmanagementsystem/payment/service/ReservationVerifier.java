package com.smartparkingmanagementsystem.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.smartparkingmanagementsystem.payment.exception.PaymentServiceUnavailableException;
import com.smartparkingmanagementsystem.payment.exception.ReservationNotFoundException;

/**
 * Verifies that a reservation exists by asking the parking service
 * {@code GET /api/parking/reservations/{id}}.
 *
 * <p>The parking service is reached through Eureka service discovery using the
 * load-balanced service name {@code lb://PARKING-SERVICE}; the actual instance
 * address is resolved at runtime, so no host/port is hardcoded here.
 *
 * <p>Kept behind a property toggle ({@code payment-service.verify-reservation},
 * default {@code true}) so the payment service can also run standalone for
 * local/demo scenarios. When enabled and the parking service is unreachable the
 * payment is refused with {@code 503} rather than being recorded blindly.
 */
@Component
public class ReservationVerifier {

    private final boolean enabled;
    private final RestClient restClient;

    public ReservationVerifier(
            @Value("${payment-service.verify-reservation:true}") boolean enabled,
            RestClient restClient) {
        this.enabled = enabled;
        this.restClient = restClient;
    }

    /**
     * @throws ReservationNotFoundException      if the parking service reports the
     *                                          reservation does not exist
     * @throws PaymentServiceUnavailableException if the parking service cannot be
     *                                           reached or returns an unexpected error
     */
    public void verifyExists(Long reservationId) {
        if (!enabled) {
            return;
        }
        try {
            restClient.get()
                    .uri("lb://PARKING-SERVICE/api/parking/reservations/{id}", reservationId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ReservationNotFoundException("Reservation not found with id: " + reservationId);
            }
            throw new PaymentServiceUnavailableException(
                    "Parking service returned " + ex.getStatusCode().value()
                            + " while verifying reservation " + reservationId);
        } catch (ResourceAccessException ex) {
            throw new PaymentServiceUnavailableException(
                    "Parking service is unreachable while verifying reservation " + reservationId);
        }
    }

}
