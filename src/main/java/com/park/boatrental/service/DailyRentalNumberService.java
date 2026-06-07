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

    /** Next rental # for the day when the boat is sent out (checked out). */
    public int nextNumberForCheckout(Long boatId, Instant sentAt) {
        DayRange day = dayRange(sentAt);
        long existing = scope == Scope.PARK
                ? rentalRepository.countBySentAtGreaterThanEqualAndSentAtLessThan(day.start(), day.end())
                : rentalRepository.countByBoat_IdAndSentAtGreaterThanEqualAndSentAtLessThan(
                        boatId, day.start(), day.end());
        return (int) existing + 1;
    }

    public int numberForRental(Rental rental) {
        if (rental.getDailyRentalNumber() > 0) {
            return rental.getDailyRentalNumber();
        }
        Instant checkout = checkoutInstant(rental);
        DayRange day = dayRange(checkout);
        long position = scope == Scope.PARK
                ? rentalRepository.countBySentAtGreaterThanEqualAndSentAtLessThanAndIdLessThanEqual(
                        day.start(), day.end(), rental.getId())
                : rentalRepository.countByBoat_IdAndSentAtGreaterThanEqualAndSentAtLessThanAndIdLessThanEqual(
                        rental.getBoat().getId(), day.start(), day.end(), rental.getId());
        return (int) position;
    }

    private Instant checkoutInstant(Rental rental) {
        return rental.getSentAt() != null ? rental.getSentAt() : rental.getAssignedAt();
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
