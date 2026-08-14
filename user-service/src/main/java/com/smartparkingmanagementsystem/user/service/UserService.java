package com.smartparkingmanagementsystem.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparkingmanagementsystem.user.dto.CreateUserRequest;
import com.smartparkingmanagementsystem.user.dto.LoginRequest;
import com.smartparkingmanagementsystem.user.dto.LoginResponse;
import com.smartparkingmanagementsystem.user.dto.UpdateUserRequest;
import com.smartparkingmanagementsystem.user.dto.UserBookingsResponse;
import com.smartparkingmanagementsystem.user.dto.UserResponse;
import com.smartparkingmanagementsystem.user.exception.DuplicateEmailException;
import com.smartparkingmanagementsystem.user.exception.InvalidCredentialsException;
import com.smartparkingmanagementsystem.user.exception.UserNotFoundException;
import com.smartparkingmanagementsystem.user.model.User;
import com.smartparkingmanagementsystem.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for user registration, authentication and profiles.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new DuplicateEmailException("Email already registered: " + request.email());
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setRole(request.role());
        return UserResponseMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return LoginResponse.of(UserResponseMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponseMapper.toResponse(findById(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findById(id);
        user.setName(request.name());
        if (!user.getEmail().equals(request.email().toLowerCase())) {
            if (userRepository.existsByEmail(request.email().toLowerCase())) {
                throw new DuplicateEmailException("Email already in use: " + request.email());
            }
            user.setEmail(request.email().toLowerCase());
        }
        user.setPhone(request.phone());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return UserResponseMapper.toResponse(userRepository.save(user));
    }

    /**
     * Placeholder for booking history. Returns an empty list until the parking /
     * booking service lands; the endpoint and response contract are already in place.
     */
    @Transactional(readOnly = true)
    public UserBookingsResponse getBookings(Long id) {
        findById(id);
        return UserBookingsResponse.empty();
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    /**
     * Internal mapper; kept private so DTO mapping stays in one place.
     */
    private static final class UserResponseMapper {

        private UserResponseMapper() {
        }

        static UserResponse toResponse(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole(),
                    user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }

}
