package com.park.boatrental.dto;

import java.util.List;

public record WaitlistStateView(
        List<WaitlistEntryView> entries,
        List<String> boatTypes,
        int nextQueueNumber
) {
}
