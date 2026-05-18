package com.park.boatrental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class BoatRentalApplication {

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("data"));
        Files.createDirectories(Path.of("exports"));
        SpringApplication.run(BoatRentalApplication.class, args);
    }
}
