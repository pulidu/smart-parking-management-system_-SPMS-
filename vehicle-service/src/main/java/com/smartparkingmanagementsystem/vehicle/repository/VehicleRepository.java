package com.smartparkingmanagementsystem.vehicle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartparkingmanagementsystem.vehicle.model.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByVehicleNumber(String vehicleNumber);

}
