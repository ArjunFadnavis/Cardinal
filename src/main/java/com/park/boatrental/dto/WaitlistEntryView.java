package com.park.boatrental.dto;

import com.park.boatrental.waitlist.RequirementNode;
import com.park.boatrental.waitlist.WaitlistStatus;

import java.time.Instant;
import java.util.List;

public record WaitlistEntryView(
        Long id,
        String customerName,
        String requirementSummary,
        RequirementNode requirement,
        WaitlistStatus status,
        Instant createdAt,
        Instant notifiedAt,
        List<String> proposedBoatNumbers
) {
}
