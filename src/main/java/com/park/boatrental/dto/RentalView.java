package com.park.boatrental.dto;

import java.time.Instant;

public record RentalView(
        Long id,
        String boatNumber,
        String boatType,
        String customerName,
        Instant assignedAt,
        Instant sentAt,
        Instant returnedAt
) {
}
