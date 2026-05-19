package com.park.boatrental.config;

import com.park.boatrental.model.Boat;
import com.park.boatrental.model.BoatStatus;
import com.park.boatrental.model.Rental;
import com.park.boatrental.repository.BoatRepository;
import com.park.boatrental.repository.RentalRepository;
import com.park.boatrental.service.DailyRentalNumberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements ApplicationRunner {

    private final BoatRepository boatRepository;
    private final RentalRepository rentalRepository;
    private final DailyRentalNumberService dailyRentalNumberService;
    private final String startupMode;

    public DataSeeder(
            BoatRepository boatRepository,
            RentalRepository rentalRepository,
            DailyRentalNumberService dailyRentalNumberService,
            @Value("${boatrental.startup.mode:normal}") String startupMode) {
        this.boatRepository = boatRepository;
        this.rentalRepository = rentalRepository;
        this.dailyRentalNumberService = dailyRentalNumberService;
        this.startupMode = startupMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (boatRepository.count() == 0) {
            seedFleet();
        }

        if (isTestMode()) {
            startAllBoatsSentOut();
        }
    }

    private void seedFleet() {
        for (int n = 1; n <= 10; n++) {
            seed("C" + n, "Canoe (2 person)");
        }
        for (int n = 1; n <= 19; n++) {
            seed("S" + n, "Kayak (1 person)");
        }
        for (int n = 1; n <= 4; n++) {
            seed("P" + n, "Pedal boat (4 person)");
        }
        for (int n = 1; n <= 9; n++) {
            seed("T" + n, "Double kayak (2 person)");
        }
        for (int n = 1; n <= 7; n++) {
            seed("U" + n, "Stand-up paddleboard (1 person)");
        }
    }

    private boolean isTestMode() {
        return "test".equalsIgnoreCase(startupMode.trim());
    }

    private void startAllBoatsSentOut() {
        Instant now = Instant.now();
        Map<Long, Rental> activeByBoatId = rentalRepository.findAllActive().stream()
                .collect(Collectors.toMap(r -> r.getBoat().getId(), r -> r, (a, b) ->
                        a.getAssignedAt().isAfter(b.getAssignedAt()) ? a : b));

        for (Boat boat : boatRepository.findAll()) {
            Rental rental = activeByBoatId.get(boat.getId());
            if (rental == null) {
                rental = new Rental();
                rental.setBoat(boat);
                rental.setCustomerName("Test customer " + boat.getBoatNumber());
                rental.setAssignedAt(now);
                rental.setSentAt(now);
                rental.setDailyRentalNumber(dailyRentalNumberService.nextNumberForAssignment(boat.getId(), now));
                rentalRepository.save(rental);
            } else if (rental.getSentAt() == null) {
                rental.setSentAt(now);
                rentalRepository.save(rental);
            }

            boat.setStatus(BoatStatus.OUT);
            boat.setWaitlistEntryId(null);
            boatRepository.save(boat);
        }
    }

    private void seed(String number, String type) {
        if (boatRepository.findByBoatNumberIgnoreCase(number).isPresent()) {
            return;
        }

        Boat boat = new Boat();
        boat.setBoatNumber(number);
        boat.setBoatType(type);
        boat.setStatus(BoatStatus.AVAILABLE);
        boatRepository.save(boat);
    }
}
