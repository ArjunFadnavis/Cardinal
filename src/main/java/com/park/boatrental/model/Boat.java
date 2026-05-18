package com.park.boatrental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "boats")
public class Boat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String boatNumber;

    @Column(nullable = false)
    private String boatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoatStatus status = BoatStatus.AVAILABLE;

    private Long waitlistEntryId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBoatNumber() {
        return boatNumber;
    }

    public void setBoatNumber(String boatNumber) {
        this.boatNumber = boatNumber;
    }

    public String getBoatType() {
        return boatType;
    }

    public void setBoatType(String boatType) {
        this.boatType = boatType;
    }

    public BoatStatus getStatus() {
        return status;
    }

    public void setStatus(BoatStatus status) {
        this.status = status;
    }

    public Long getWaitlistEntryId() {
        return waitlistEntryId;
    }

    public void setWaitlistEntryId(Long waitlistEntryId) {
        this.waitlistEntryId = waitlistEntryId;
    }
}
