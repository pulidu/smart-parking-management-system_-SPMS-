package com.smartparkingmanagementsystem.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartparkingmanagementsystem.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

}
