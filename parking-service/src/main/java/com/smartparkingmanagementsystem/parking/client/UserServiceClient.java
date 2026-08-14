package com.smartparkingmanagementsystem.parking.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.smartparkingmanagementsystem.parking.client.dto.UserInfoDto;
import com.smartparkingmanagementsystem.parking.exception.ReferencedResourceNotFoundException;
import com.smartparkingmanagementsystem.parking.exception.UpstreamServiceUnavailableException;

/**
 * Client for the User Service, reached through Eureka service discovery
 * ({@code lb://USER-SERVICE}). Validates that the user referenced by a
 * reservation actually exists.
 *
 * <ul>
 *   <li>user does not exist      -&gt; 404 {@link ReferencedResourceNotFoundException}</li>
 *   <li>user service unreachable -&gt; 503 {@link UpstreamServiceUnavailableException}</li>
 * </ul>
 */
@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public UserInfoDto getUser(Long userId) {
        try {
            return restClient.get()
                    .uri("lb://USER-SERVICE/api/users/{id}", userId)
                    .retrieve()
                    .body(UserInfoDto.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ReferencedResourceNotFoundException("User not found with id: " + userId);
            }
            throw new UpstreamServiceUnavailableException(
                    "User service returned " + ex.getStatusCode().value()
                            + " while fetching user " + userId);
        } catch (ResourceAccessException ex) {
            throw new UpstreamServiceUnavailableException(
                    "User service is unreachable while fetching user " + userId);
        }
    }

}
