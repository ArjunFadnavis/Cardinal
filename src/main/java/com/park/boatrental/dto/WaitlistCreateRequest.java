package com.park.boatrental.dto;

import com.park.boatrental.waitlist.RequirementNode;

public record WaitlistCreateRequest(String customerName, RequirementNode requirement) {
}
