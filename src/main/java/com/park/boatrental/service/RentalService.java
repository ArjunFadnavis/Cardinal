package com.park.boatrental.service;

import com.park.boatrental.dto.AssignRequest;
import com.park.boatrental.dto.BoatView;
import com.park.boatrental.dto.RentalView;
import com.park.boatrental.model.Boat;
import com.park.boatrental.model.BoatStatus;
import com.park.boatrental.model.Rental;
import com.park.boatrental.model.WaitlistEntry;
import com.park.boatrental.repository.BoatRepository;
import com.park.boatrental.repository.RentalRepository;
import com.park.boatrental.repository.WaitlistEntryRepository;
import com.park.boatrental.util.BoatNumberComparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RentalService {

    private final BoatRepository boatRepository;
    private final RentalRepository rentalRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final DailyRentalNumberService dailyRentalNumberService;
    private final WaitlistService waitlistService;

    public RentalService(
            BoatRepository boatRepository,
            RentalRepository rentalRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            DailyRentalNumberService dailyRentalNumberService,
            WaitlistService waitlistService) {
        this.boatRepository = boatRepository;
        this.rentalRepository = rentalRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.dailyRentalNumberService = dailyRentalNumberService;
        this.waitlistService = waitlistService;
    }

    @Transactional(readOnly = true)
    public List<BoatView> listBoats() {
        Map<Long, Rental> activeByBoatId = rentalRepository.findAllActive().stream()
                .collect(Collectors.toMap(r -> r.getBoat().getId(), r -> r, (a, b) ->
                        a.getAssignedAt().isAfter(b.getAssignedAt()) ? a : b));

        return boatRepository.findAll().stream()
                .sorted(BoatNumberComparator.INSTANCE)
                .map(boat -> toBoatView(boat, activeByBoatId.get(boat.getId())))
                .toList();
    }

    @Transactional
    public BoatView assign(String boatNumber, AssignRequest request) {
        String name = normalizeName(request.customerName());
        Boat boat = findBoat(boatNumber);

        if (boat.getStatus() != BoatStatus.AVAILABLE && boat.getStatus() != BoatStatus.WAITLISTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Boat " + boat.getBoatNumber() + " is not available (status: " + boat.getStatus() + ")");
        }

        Instant now = Instant.now();
        Rental rental = new Rental();
        rental.setBoat(boat);
        rental.setCustomerName(name);
        rental.setAssignedAt(now);
        rental.setDailyRentalNumber(dailyRentalNumberService.nextNumberForAssignment(boat.getId(), now));
        rentalRepository.save(rental);

        boat.setStatus(BoatStatus.ASSIGNED);
        boat.setWaitlistEntryId(null);
        boatRepository.save(boat);

        return toBoatView(boat, rental);
    }

    @Transactional
    public BoatView sendOut(String boatNumber) {
        Boat boat = findBoat(boatNumber);

        if (boat.getStatus() != BoatStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Boat " + boat.getBoatNumber() + " is not waiting to go out (status: " + boat.getStatus() + ")");
        }

        Rental rental = rentalRepository
                .findFirstByBoat_IdAndReturnedAtIsNullOrderByAssignedAtDesc(boat.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No active rental found for boat " + boat.getBoatNumber()));

        rental.setSentAt(Instant.now());
        boat.setStatus(BoatStatus.OUT);
        rentalRepository.save(rental);
        boatRepository.save(boat);

        return toBoatView(boat, rental);
    }

    @Transactional
    public BoatView returnBoat(String boatNumber) {
        Boat boat = findBoat(boatNumber);

        if (boat.getStatus() != BoatStatus.OUT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Boat " + boat.getBoatNumber() + " is not out on the water (status: " + boat.getStatus() + ")");
        }

        Rental rental = rentalRepository
                .findFirstByBoat_IdAndReturnedAtIsNullOrderByAssignedAtDesc(boat.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No active rental found for boat " + boat.getBoatNumber()));

        rental.setReturnedAt(Instant.now());
        boat.setStatus(BoatStatus.AVAILABLE);
        rentalRepository.save(rental);
        boatRepository.save(boat);

        waitlistService.runMatcherAfterReturn();

        return toBoatView(boat, null);
    }

    @Transactional(readOnly = true)
    public List<RentalView> listActiveRentals() {
        return rentalRepository.findAllActive().stream()
                .sorted(Comparator.comparing((Rental r) -> r.getBoat().getBoatType())
                        .thenComparing(r -> r.getBoat().getBoatNumber(), BoatNumberComparator::compareNumbers))
                .map(this::toRentalView)
                .toList();
    }

    private Boat findBoat(String boatNumber) {
        return boatRepository.findByBoatNumberIgnoreCase(boatNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Boat not found: " + boatNumber));
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required");
        }
        return name.trim();
    }

    private BoatView toBoatView(Boat boat, Rental activeRental) {
        if (activeRental != null) {
            return new BoatView(
                    boat.getId(),
                    boat.getBoatNumber(),
                    boat.getBoatType(),
                    boat.getStatus(),
                    activeRental.getId(),
                    activeRental.getCustomerName(),
                    activeRental.getAssignedAt(),
                    activeRental.getSentAt(),
                    boat.getWaitlistEntryId());
        }
        String customer = null;
        if (boat.getStatus() == BoatStatus.WAITLISTED && boat.getWaitlistEntryId() != null) {
            customer = waitlistEntryRepository.findById(boat.getWaitlistEntryId())
                    .map(WaitlistEntry::getCustomerName)
                    .orElse(null);
        }
        return new BoatView(
                boat.getId(),
                boat.getBoatNumber(),
                boat.getBoatType(),
                boat.getStatus(),
                null,
                customer,
                null,
                null,
                boat.getWaitlistEntryId());
    }

    private RentalView toRentalView(Rental rental) {
        Boat boat = rental.getBoat();
        return new RentalView(
                rental.getId(),
                boat.getBoatNumber(),
                boat.getBoatType(),
                rental.getCustomerName(),
                rental.getAssignedAt(),
                rental.getSentAt(),
                rental.getReturnedAt());
    }
}
