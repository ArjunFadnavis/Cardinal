package com.park.boatrental.web;

import com.park.boatrental.dto.WaitlistCreateRequest;
import com.park.boatrental.dto.WaitlistEntryView;
import com.park.boatrental.dto.WaitlistStateView;
import com.park.boatrental.service.WaitlistService;
import com.park.boatrental.dto.WaitlistUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @GetMapping
    public WaitlistStateView getState() {
        return waitlistService.getState();
    }

    @PostMapping
    public WaitlistEntryView add(@RequestBody WaitlistCreateRequest request) {
        return waitlistService.add(request);
    }

    @PostMapping("/{id}/approve")
    public WaitlistEntryView approve(@PathVariable Long id) {
        return waitlistService.approve(id);
    }

    @PutMapping("/{id}")
    public WaitlistEntryView update(@PathVariable Long id, @RequestBody WaitlistUpdateRequest request) {
        return waitlistService.update(id, request);
    }

    @PostMapping("/{id}/requeue")
    public WaitlistEntryView requeueAtTop(@PathVariable Long id) {
        return waitlistService.requeueAtTop(id);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        waitlistService.remove(id);
    }
}
