package com.park.boatrental.web;

import com.park.boatrental.dto.AssignRequest;
import com.park.boatrental.dto.BoatView;
import com.park.boatrental.dto.RentalView;
import com.park.boatrental.service.RentalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RentalController {

    private final RentalService rentalService;

    public RentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @GetMapping("/boats")
    public List<BoatView> listBoats() {
        return rentalService.listBoats();
    }

    @GetMapping("/rentals/active")
    public List<RentalView> listActiveRentals() {
        return rentalService.listActiveRentals();
    }

    @PostMapping("/boats/{boatNumber}/assign")
    public BoatView assign(@PathVariable String boatNumber, @RequestBody AssignRequest request) {
        return rentalService.assign(boatNumber, request);
    }

    @PostMapping("/boats/{boatNumber}/send")
    public BoatView sendOut(@PathVariable String boatNumber) {
        return rentalService.sendOut(boatNumber);
    }

    @PostMapping("/boats/{boatNumber}/return")
    public BoatView returnBoat(@PathVariable String boatNumber) {
        return rentalService.returnBoat(boatNumber);
    }
}
