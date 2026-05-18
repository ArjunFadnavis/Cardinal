package com.park.boatrental.config;

import com.park.boatrental.model.Boat;
import com.park.boatrental.model.BoatStatus;
import com.park.boatrental.repository.BoatRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

    private final BoatRepository boatRepository;

    public DataSeeder(BoatRepository boatRepository) {
        this.boatRepository = boatRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (boatRepository.count() > 0) {
            return;
        }

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

    private void seed(String number, String type) {
        Boat boat = new Boat();
        boat.setBoatNumber(number);
        boat.setBoatType(type);
        boat.setStatus(BoatStatus.AVAILABLE);
        boatRepository.save(boat);
    }
}
