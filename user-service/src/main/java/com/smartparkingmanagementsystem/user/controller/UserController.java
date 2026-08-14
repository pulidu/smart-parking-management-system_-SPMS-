package com.smartparkingmanagementsystem.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartparkingmanagementsystem.user.dto.CreateUserRequest;
import com.smartparkingmanagementsystem.user.dto.LoginRequest;
import com.smartparkingmanagementsystem.user.dto.LoginResponse;
import com.smartparkingmanagementsystem.user.dto.UpdateUserRequest;
import com.smartparkingmanagementsystem.user.dto.UserBookingsResponse;
import com.smartparkingmanagementsystem.user.dto.UserResponse;
import com.smartparkingmanagementsystem.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @GetMapping("/{id}/bookings")
    public UserBookingsResponse getBookings(@PathVariable Long id) {
        return userService.getBookings(id);
    }

}
