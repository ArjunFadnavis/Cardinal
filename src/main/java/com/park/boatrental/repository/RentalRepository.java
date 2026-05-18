package com.park.boatrental.repository;

import com.park.boatrental.model.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RentalRepository extends JpaRepository<Rental, Long> {

    Optional<Rental> findFirstByBoat_IdAndReturnedAtIsNullOrderByAssignedAtDesc(Long boatId);

    @Query("SELECT r FROM Rental r JOIN FETCH r.boat WHERE r.returnedAt IS NULL")
    List<Rental> findAllActive();

    long countByBoat_IdAndAssignedAtGreaterThanEqualAndAssignedAtLessThan(
            Long boatId, Instant startInclusive, Instant endExclusive);

    long countByAssignedAtGreaterThanEqualAndAssignedAtLessThan(
            Instant startInclusive, Instant endExclusive);

    long countByBoat_IdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndIdLessThanEqual(
            Long boatId, Instant startInclusive, Instant endExclusive, Long id);

    long countByAssignedAtGreaterThanEqualAndAssignedAtLessThanAndIdLessThanEqual(
            Instant startInclusive, Instant endExclusive, Long id);

    @Query("SELECT r FROM Rental r JOIN FETCH r.boat WHERE r.returnedAt IS NOT NULL AND r.exportedAt IS NULL ORDER BY r.returnedAt")
    List<Rental> findCompletedNotExported();
}
