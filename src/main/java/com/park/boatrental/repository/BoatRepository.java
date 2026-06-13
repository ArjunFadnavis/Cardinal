package com.park.boatrental.repository;

import com.park.boatrental.model.Boat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.park.boatrental.model.BoatStatus;

import java.util.List;
import java.util.Optional;

public interface BoatRepository extends JpaRepository<Boat, Long> {

    Optional<Boat> findByBoatNumberIgnoreCase(String boatNumber);

    List<Boat> findByStatus(BoatStatus status);

    List<Boat> findByWaitlistEntryId(Long waitlistEntryId);

    @Query("SELECT DISTINCT b.boatType FROM Boat b ORDER BY b.boatType")
    List<String> findAllDistinctBoatTypes();
}
