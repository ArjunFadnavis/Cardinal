package com.park.boatrental.service;

import com.park.boatrental.dto.WaitlistCreateRequest;
import com.park.boatrental.dto.WaitlistEntryView;
import com.park.boatrental.dto.WaitlistStateView;
import com.park.boatrental.dto.WaitlistUpdateRequest;
import com.park.boatrental.model.Boat;
import com.park.boatrental.model.BoatStatus;
import com.park.boatrental.model.WaitlistEntry;
import com.park.boatrental.repository.BoatRepository;
import com.park.boatrental.repository.WaitlistEntryRepository;
import com.park.boatrental.util.BoatNumberComparator;
import com.park.boatrental.waitlist.PartyPools;
import com.park.boatrental.waitlist.RequirementJson;
import com.park.boatrental.waitlist.RequirementNode;
import com.park.boatrental.waitlist.WaitlistMatcher;
import com.park.boatrental.waitlist.WaitlistStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final BoatRepository boatRepository;

    public WaitlistService(WaitlistEntryRepository waitlistEntryRepository, BoatRepository boatRepository) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.boatRepository = boatRepository;
    }

    @Transactional(readOnly = true)
    public WaitlistStateView getState() {
        List<WaitlistEntry> active = waitlistEntryRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED));
        List<String> boatTypes = boatRepository.findAll().stream()
                .map(Boat::getBoatType)
                .distinct()
                .sorted()
                .toList();
        return new WaitlistStateView(
                active.stream().map(this::toView).toList(),
                boatTypes,
                waitlistEntryRepository.findMaxQueueNumber() + 1);
    }

    @Transactional
    public WaitlistEntryView add(WaitlistCreateRequest request) {
        String name = normalizeName(request.customerName());
        RequirementNode requirement = request.requirement();
        validateRequirement(requirement);

        WaitlistEntry entry = new WaitlistEntry();
        entry.setCustomerName(name);
        entry.setQueueNumber(requireQueueNumber(request.queueNumber()));
        entry.setRequirementJson(RequirementJson.write(requirement));
        entry.setRequirementSummary(WaitlistMatcher.summarize(requirement));
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setCreatedAt(Instant.now());
        waitlistEntryRepository.save(entry);

        runMatcher();
        return toView(waitlistEntryRepository.findById(entry.getId()).orElseThrow());
    }

    @Transactional
    public void remove(Long entryId) {
        WaitlistEntry entry = findEntry(entryId);
        if (entry.getStatus() == WaitlistStatus.FULFILLED) {
            releaseBoatsForEntry(entry.getId());
        }
        entry.setStatus(WaitlistStatus.REMOVED);
        waitlistEntryRepository.save(entry);
        runMatcher();
    }

    @Transactional
    public WaitlistEntryView update(Long entryId, WaitlistUpdateRequest request) {
        WaitlistEntry entry = findEntry(entryId);
        if (entry.getStatus() != WaitlistStatus.WAITING && entry.getStatus() != WaitlistStatus.NOTIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only waiting or notified entries can be edited");
        }

        String name = request.customerName() != null && !request.customerName().isBlank()
                ? normalizeName(request.customerName())
                : entry.getCustomerName();
        RequirementNode requirement = request.requirement();
        validateRequirement(requirement);

        entry.setCustomerName(name);
        entry.setQueueNumber(normalizeQueueNumber(request.queueNumber()));
        entry.setRequirementJson(RequirementJson.write(requirement));
        entry.setRequirementSummary(WaitlistMatcher.summarize(requirement));
        clearNotification(entry);
        entry.setStatus(WaitlistStatus.WAITING);
        waitlistEntryRepository.save(entry);

        runMatcher();
        return toView(waitlistEntryRepository.findById(entry.getId()).orElseThrow());
    }

    @Transactional
    public WaitlistEntryView requeueAtTop(Long entryId) {
        WaitlistEntry entry = findEntry(entryId);
        if (entry.getStatus() != WaitlistStatus.NOTIFIED && entry.getStatus() != WaitlistStatus.FULFILLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only called (notified) or held (fulfilled) entries can be requeued");
        }

        if (entry.getStatus() == WaitlistStatus.FULFILLED) {
            releaseBoatsForEntry(entry.getId());
        }

        clearNotification(entry);
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setCreatedAt(topOfQueueTimestamp());
        waitlistEntryRepository.save(entry);

        runMatcher();
        return toView(entry);
    }

    @Transactional
    public WaitlistEntryView approve(Long entryId) {
        WaitlistEntry entry = findEntry(entryId);
        if (entry.getStatus() != WaitlistStatus.NOTIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only notified waitlist entries can be approved");
        }

        List<Boat> proposed = loadProposedBoats(entry);
        for (Boat boat : proposed) {
            if (boat.getStatus() != BoatStatus.AVAILABLE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Boat " + boat.getBoatNumber() + " is no longer available");
            }
            boat.setStatus(BoatStatus.WAITLISTED);
            boat.setWaitlistEntryId(entry.getId());
            boatRepository.save(boat);
        }

        entry.setStatus(WaitlistStatus.FULFILLED);
        waitlistEntryRepository.save(entry);
        runMatcher();
        return toView(entry);
    }

    @Transactional
    public void runMatcherAfterReturn() {
        runMatcher();
    }

    private void runMatcher() {
        clearStaleNotifications();

        List<Boat> available = boatRepository.findByStatus(BoatStatus.AVAILABLE);
        List<WaitlistEntry> waiting = waitlistEntryRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.WAITING);

        Set<Long> claimedBoatIds = new HashSet<>();

        for (WaitlistEntry entry : waiting) {
            List<Boat> pool = available.stream()
                    .filter(b -> !claimedBoatIds.contains(b.getId()))
                    .toList();

            RequirementNode requirement = RequirementJson.read(entry.getRequirementJson());
            Optional<List<Boat>> match = WaitlistMatcher.tryMatch(requirement, pool);

            if (match.isPresent()) {
                notifyEntry(entry, match.get());
                match.get().forEach(b -> claimedBoatIds.add(b.getId()));
            } else {
                Set<Long> claims = WaitlistMatcher.boatsClaimedWhenUnsatisfied(requirement, pool);
                claimedBoatIds.addAll(claims);
            }
        }
    }

    private void clearStaleNotifications() {
        List<WaitlistEntry> notified = waitlistEntryRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.NOTIFIED);
        for (WaitlistEntry entry : notified) {
            RequirementNode requirement = RequirementJson.read(entry.getRequirementJson());
            List<Boat> available = boatRepository.findByStatus(BoatStatus.AVAILABLE);
            Optional<List<Boat>> match = WaitlistMatcher.tryMatch(requirement, available);
            List<Boat> proposed = loadProposedBoats(entry);

            boolean stillValid = match.isPresent()
                    && sameBoatSet(match.get(), proposed);
            if (!stillValid) {
                entry.setStatus(WaitlistStatus.WAITING);
                entry.setNotifiedAt(null);
                entry.setProposedBoatIds(null);
                waitlistEntryRepository.save(entry);
            }
        }
    }

    private void clearNotification(WaitlistEntry entry) {
        entry.setNotifiedAt(null);
        entry.setProposedBoatIds(null);
    }

    private Instant topOfQueueTimestamp() {
        return waitlistEntryRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.WAITING).stream()
                .map(WaitlistEntry::getCreatedAt)
                .min(Instant::compareTo)
                .map(t -> t.minusMillis(1))
                .orElse(Instant.now());
    }

    private void notifyEntry(WaitlistEntry entry, List<Boat> boats) {
        entry.setStatus(WaitlistStatus.NOTIFIED);
        entry.setNotifiedAt(Instant.now());
        entry.setProposedBoatIds(boats.stream()
                .map(b -> Long.toString(b.getId()))
                .collect(Collectors.joining(",")));
        waitlistEntryRepository.save(entry);
    }

    private List<Boat> loadProposedBoats(WaitlistEntry entry) {
        if (entry.getProposedBoatIds() == null || entry.getProposedBoatIds().isBlank()) {
            return List.of();
        }
        List<Long> ids = Arrays.stream(entry.getProposedBoatIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return boatRepository.findAllById(ids);
    }

    private void releaseBoatsForEntry(Long entryId) {
        List<Boat> boats = boatRepository.findByWaitlistEntryId(entryId);
        for (Boat boat : boats) {
            boat.setStatus(BoatStatus.AVAILABLE);
            boat.setWaitlistEntryId(null);
            boatRepository.save(boat);
        }
    }

    private boolean sameBoatSet(List<Boat> a, List<Boat> b) {
        Set<Long> aIds = a.stream().map(Boat::getId).collect(Collectors.toSet());
        Set<Long> bIds = b.stream().map(Boat::getId).collect(Collectors.toSet());
        return aIds.equals(bIds);
    }

    private void validateRequirement(RequirementNode requirement) {
        if (requirement instanceof RequirementNode.BoatReq boat) {
            if (boat.boatType() == null || boat.boatType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boat type is required");
            }
            return;
        }
        if (requirement instanceof RequirementNode.AndReq and) {
            if (and.children().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AND group needs at least one item");
            }
            and.children().forEach(this::validateRequirement);
            return;
        }
        if (requirement instanceof RequirementNode.OrReq or) {
            if (or.children().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OR group needs at least one option");
            }
            or.children().forEach(this::validateRequirement);
            return;
        }
        if (requirement instanceof RequirementNode.PartyReq party) {
            PartyPools pools = PartyPools.from(party);
            if (pools.total() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least one person in the party");
            }
            if (!pools.isStructurallyValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Children need at least one adult in the party");
            }
        }
    }

    private WaitlistEntry findEntry(Long id) {
        return waitlistEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waitlist entry not found"));
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required");
        }
        return name.trim();
    }

    private static Integer normalizeQueueNumber(Integer queueNumber) {
        if (queueNumber == null) {
            return null;
        }
        if (queueNumber < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call number must be at least 1");
        }
        return queueNumber;
    }

    private static Integer requireQueueNumber(Integer queueNumber) {
        Integer normalized = normalizeQueueNumber(queueNumber);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call number is required");
        }
        return normalized;
    }

    private WaitlistEntryView toView(WaitlistEntry entry) {
        List<String> proposedBoatNumbers = new ArrayList<>();
        if (entry.getProposedBoatIds() != null && !entry.getProposedBoatIds().isBlank()) {
            proposedBoatNumbers = loadProposedBoats(entry).stream()
                    .map(Boat::getBoatNumber)
                    .sorted(BoatNumberComparator::compareNumbers)
                    .toList();
        }
        RequirementNode requirement = RequirementJson.read(entry.getRequirementJson());
        return new WaitlistEntryView(
                entry.getId(),
                entry.getCustomerName(),
                entry.getQueueNumber(),
                entry.getRequirementSummary(),
                requirement,
                entry.getStatus(),
                entry.getCreatedAt(),
                entry.getNotifiedAt(),
                proposedBoatNumbers);
    }
}
