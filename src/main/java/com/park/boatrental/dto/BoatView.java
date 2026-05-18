package com.park.boatrental.dto;

import com.park.boatrental.model.BoatStatus;

import java.time.Instant;

public record BoatView(
        Long id,
        String boatNumber,
        String boatType,
        BoatStatus status,
        Long activeRentalId,
        String customerName,
        Instant assignedAt,
        Instant sentAt,
        Long waitlistEntryId
) {
}
