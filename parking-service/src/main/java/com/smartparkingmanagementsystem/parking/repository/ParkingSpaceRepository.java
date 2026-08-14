package com.smartparkingmanagementsystem.parking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartparkingmanagementsystem.parking.model.ParkingSpace;
import com.smartparkingmanagementsystem.parking.model.ParkingSpaceStatus;

import jakarta.persistence.LockModeType;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    /**
     * Search with optional city / zone filters and an optional set of statuses.
     * When {@code noStatusFilter} is true the statuses list is ignored (any
     * status matches); otherwise the spaces must match one of the statuses.
     */
    @Query("""
            select s from ParkingSpace s
            where (:city is null or lower(s.city) = lower(:city))
              and (:zone is null or s.zone = :zone)
              and (:noStatusFilter = true or s.status in :statuses)
            order by s.spaceNumber asc
            """)
    List<ParkingSpace> search(@Param("city") String city,
                              @Param("zone") String zone,
                              @Param("noStatusFilter") boolean noStatusFilter,
                              @Param("statuses") List<ParkingSpaceStatus> statuses);

    /**
     * Locks the parking space row for update so two concurrent reservation
     * attempts on the same space serialize and cannot both pass the
     * "must be AVAILABLE" check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpace s where s.id = :id")
    Optional<ParkingSpace> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOwnerIdAndSpaceNumber(Long ownerId, String spaceNumber);

}
