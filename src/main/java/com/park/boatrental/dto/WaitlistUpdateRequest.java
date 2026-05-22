package com.park.boatrental.dto;

import com.park.boatrental.waitlist.RequirementNode;

public record WaitlistUpdateRequest(String customerName, Integer queueNumber, RequirementNode requirement) {
}
