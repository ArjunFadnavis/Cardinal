package com.park.boatrental.repository;

import com.park.boatrental.model.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countBySentAtGreaterThanEqualAndSentAtLessThan(
            Instant startInclusive, Instant endExclusive);

    @Query("""
            SELECT COUNT(r) FROM Rental r
            WHERE r.sentAt >= :start AND r.sentAt < :end
            AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    long countSentOutInDayExcludingId(
            @Param("start") Instant startInclusive,
            @Param("end") Instant endExclusive,
            @Param("excludeId") Long excludeId);

    long countByBoat_IdAndSentAtGreaterThanEqualAndSentAtLessThan(
            Long boatId, Instant startInclusive, Instant endExclusive);

    @Query("""
            SELECT COUNT(r) FROM Rental r
            WHERE r.boat.id = :boatId
            AND r.sentAt >= :start AND r.sentAt < :end
            AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    long countSentOutInDayForBoatExcludingId(
            @Param("boatId") Long boatId,
            @Param("start") Instant startInclusive,
            @Param("end") Instant endExclusive,
            @Param("excludeId") Long excludeId);

    long countBySentAtGreaterThanEqualAndSentAtLessThanAndIdLessThanEqual(
            Instant startInclusive, Instant endExclusive, Long id);

    long countByBoat_IdAndSentAtGreaterThanEqualAndSentAtLessThanAndIdLessThanEqual(
            Long boatId, Instant startInclusive, Instant endExclusive, Long id);

    @Query("""
            SELECT r FROM Rental r JOIN FETCH r.boat
            WHERE r.returnedAt IS NOT NULL AND r.exportedAt IS NULL
            ORDER BY
                CASE WHEN r.dailyRentalNumber > 0 THEN r.dailyRentalNumber ELSE 2147483647 END,
                COALESCE(r.sentAt, r.assignedAt),
                r.id
            """)
    List<Rental> findCompletedNotExported();
}
