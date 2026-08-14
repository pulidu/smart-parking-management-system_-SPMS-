package com.smartparkingmanagementsystem.parking.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.smartparkingmanagementsystem.parking.client.dto.VehicleInfoDto;
import com.smartparkingmanagementsystem.parking.exception.ReferencedResourceNotFoundException;
import com.smartparkingmanagementsystem.parking.exception.UpstreamServiceUnavailableException;
import com.smartparkingmanagementsystem.parking.exception.VehicleOwnershipException;

/**
 * Client for the Vehicle Service, reached through Eureka service discovery
 * ({@code lb://VEHICLE-SERVICE}). Fetches a vehicle and enforces the parking
 * domain rule that a reservation may only reference a vehicle belonging to the
 * user who makes the reservation.
 *
 * <ul>
 *   <li>vehicle does not exist           -&gt; 404 {@link ReferencedResourceNotFoundException}</li>
 *   <li>vehicle belongs to another user  -&gt; 409 {@link VehicleOwnershipException}</li>
 *   <li>vehicle service unreachable      -&gt; 503 {@link UpstreamServiceUnavailableException}</li>
 * </ul>
 */
@Component
public class VehicleServiceClient {

    private final RestClient restClient;

    public VehicleServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return the vehicle when it exists and is owned by {@code userId}
     * @throws ReferencedResourceNotFoundException if the vehicle does not exist
     * @throws VehicleOwnershipException           if the vehicle belongs to another user
     * @throws UpstreamServiceUnavailableException if the Vehicle Service cannot be reached
     */
    public VehicleInfoDto getVehicleOwnedBy(Long vehicleId, Long userId) {
        VehicleInfoDto vehicle = fetch(vehicleId);
        if (!vehicle.userId().equals(userId)) {
            throw new VehicleOwnershipException(
                    "Vehicle " + vehicleId + " does not belong to user " + userId);
        }
        return vehicle;
    }

    private VehicleInfoDto fetch(Long vehicleId) {
        try {
            return restClient.get()
                    .uri("lb://VEHICLE-SERVICE/api/vehicles/{id}", vehicleId)
                    .retrieve()
                    .body(VehicleInfoDto.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ReferencedResourceNotFoundException("Vehicle not found with id: " + vehicleId);
            }
            throw new UpstreamServiceUnavailableException(
                    "Vehicle service returned " + ex.getStatusCode().value()
                            + " while fetching vehicle " + vehicleId);
        } catch (ResourceAccessException ex) {
            throw new UpstreamServiceUnavailableException(
                    "Vehicle service is unreachable while fetching vehicle " + vehicleId);
        }
    }

}
