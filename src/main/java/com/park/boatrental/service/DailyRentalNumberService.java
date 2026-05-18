package com.park.boatrental.service;

import com.park.boatrental.model.Rental;
import com.park.boatrental.repository.RentalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DailyRentalNumberService {

    public enum Scope {
        BOAT,
        PARK
    }

    private final RentalRepository rentalRepository;
    private final ZoneId zoneId;
    private final Scope scope;

    public DailyRentalNumberService(
            RentalRepository rentalRepository,
            @Value("${boatrental.timezone}") String timezone,
            @Value("${boatrental.rental-number.scope:boat}") String scopeProperty) {
        this.rentalRepository = rentalRepository;
        this.zoneId = ZoneId.of(timezone);
        this.scope = Scope.valueOf(scopeProperty.trim().toUpperCase());
    }

    public int nextNumberForAssignment(Long boatId, Instant assignedAt) {
        DayRange day = dayRange(assignedAt);
        long existing = scope == Scope.PARK
                ? rentalRepository.countByAssignedAtGreaterThanEqualAndAssignedAtLessThan(day.start(), day.end())
                : rentalRepository.countByBoat_IdAndAssignedAtGreaterThanEqualAndAssignedAtLessThan(
                        boatId, day.start(), day.end());
        return (int) existing + 1;
    }

    public int numberForRental(Rental rental) {
        DayRange day = dayRange(rental.getAssignedAt());
        long position = scope == Scope.PARK
                ? rentalRepository.countByAssignedAtGreaterThanEqualAndAssignedAtLessThanAndIdLessThanEqual(
                        day.start(), day.end(), rental.getId())
                : rentalRepository.countByBoat_IdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndIdLessThanEqual(
                        rental.getBoat().getId(), day.start(), day.end(), rental.getId());
        return (int) position;
    }

    private DayRange dayRange(Instant instant) {
        LocalDate date = instant.atZone(zoneId).toLocalDate();
        Instant start = date.atStartOfDay(zoneId).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return new DayRange(start, end);
    }

    private record DayRange(Instant start, Instant end) {
    }
}
