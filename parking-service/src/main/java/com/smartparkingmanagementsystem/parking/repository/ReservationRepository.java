package com.smartparkingmanagementsystem.parking.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartparkingmanagementsystem.parking.model.Reservation;
import com.smartparkingmanagementsystem.parking.model.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByParkingSpaceIdAndStatusIn(Long parkingSpaceId, Collection<ReservationStatus> statuses);

    /**
     * Locks the reservation row for update so concurrent cancel/release attempts
     * serialize and cannot both act on the same active reservation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

}
