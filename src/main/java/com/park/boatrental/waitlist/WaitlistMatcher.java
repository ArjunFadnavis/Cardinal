package com.park.boatrental.waitlist;

import com.park.boatrental.model.Boat;
import com.park.boatrental.util.BoatCapacity;
import com.park.boatrental.util.BoatNumberComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class WaitlistMatcher {

    private WaitlistMatcher() {
    }

    public static Optional<List<Boat>> tryMatch(RequirementNode requirement, List<Boat> available) {
        List<Boat> pool = sortedCopy(available);
        return match(requirement, pool);
    }

    public static Set<Long> boatsClaimedWhenUnsatisfied(RequirementNode requirement, List<Boat> available) {
        if (tryMatch(requirement, available).isPresent()) {
            return Set.of();
        }
        Set<Long> claimed = new LinkedHashSet<>();
        claimRecursive(requirement, sortedCopy(available), claimed);
        return claimed;
    }

    private static void claimRecursive(RequirementNode requirement, List<Boat> available, Set<Long> claimed) {
        if (requirement instanceof RequirementNode.BoatReq boat) {
            reserveBoats(available, boat.boatType(), boat.quantity()).forEach(b -> claimed.add(b.getId()));
        } else if (requirement instanceof RequirementNode.AndReq and) {
            List<Boat> remaining = new ArrayList<>(available);
            for (RequirementNode child : and.children()) {
                claimRecursive(child, remaining, claimed);
                remaining = remaining.stream()
                        .filter(b -> !claimed.contains(b.getId()))
                        .toList();
            }
        } else if (requirement instanceof RequirementNode.OrReq or) {
            for (RequirementNode child : or.children()) {
                claimRecursive(child, available, claimed);
            }
        } else if (requirement instanceof RequirementNode.PartyReq party) {
            List<Boat> eligible = eligibleForParty(party, available);
            findMaxSubsetNotExceeding(eligible, party.partySize())
                    .forEach(b -> claimed.add(b.getId()));
        }
    }

    private static Optional<List<Boat>> match(RequirementNode requirement, List<Boat> available) {
        if (requirement instanceof RequirementNode.BoatReq boat) {
            List<Boat> picked = pickBoats(available, boat.boatType(), boat.quantity());
            return picked.size() >= boat.quantity() ? Optional.of(picked) : Optional.empty();
        }
        if (requirement instanceof RequirementNode.AndReq and) {
            return matchAnd(and.children(), available);
        }
        if (requirement instanceof RequirementNode.OrReq or) {
            return matchOr(or.children(), available);
        }
        if (requirement instanceof RequirementNode.PartyReq party) {
            return matchParty(party, available);
        }
        return Optional.empty();
    }

    private static Optional<List<Boat>> matchAnd(List<RequirementNode> children, List<Boat> available) {
        if (children.isEmpty()) {
            return Optional.empty();
        }
        List<Boat> used = new ArrayList<>();
        List<Boat> remaining = new ArrayList<>(available);
        for (RequirementNode child : children) {
            Optional<List<Boat>> matched = match(child, remaining);
            if (matched.isEmpty()) {
                return Optional.empty();
            }
            used.addAll(matched.get());
            Set<Long> usedIds = matched.get().stream().map(Boat::getId).collect(Collectors.toSet());
            remaining = remaining.stream().filter(b -> !usedIds.contains(b.getId())).toList();
        }
        return Optional.of(used);
    }

    private static Optional<List<Boat>> matchOr(List<RequirementNode> children, List<Boat> available) {
        for (RequirementNode child : children) {
            Optional<List<Boat>> matched = match(child, available);
            if (matched.isPresent()) {
                return matched;
            }
        }
        return Optional.empty();
    }

    private static Optional<List<Boat>> matchParty(RequirementNode.PartyReq party, List<Boat> available) {
        return findExactSeatCombination(eligibleForParty(party, available), party.partySize());
    }

    private static List<Boat> eligibleForParty(RequirementNode.PartyReq party, List<Boat> available) {
        return available.stream()
                .filter(b -> !party.excludeTypes().contains(b.getBoatType()))
                .sorted(Comparator.comparing(Boat::getBoatNumber, BoatNumberComparator::compareNumbers))
                .toList();
    }

    private static Optional<List<Boat>> findExactSeatCombination(List<Boat> boats, int targetSeats) {
        List<Boat> best = new ArrayList<>();
        searchExactCombination(boats, 0, targetSeats, 0, new ArrayList<>(), best);
        return best.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(best));
    }

    private static void searchExactCombination(
            List<Boat> boats,
            int index,
            int targetSeats,
            int sum,
            List<Boat> current,
            List<Boat> best) {
        if (sum == targetSeats) {
            if (isPreferredBoatSet(current, best)) {
                best.clear();
                best.addAll(current);
            }
            return;
        }
        if (sum > targetSeats || index >= boats.size()) {
            return;
        }
        searchExactCombination(boats, index + 1, targetSeats, sum, current, best);
        Boat boat = boats.get(index);
        current.add(boat);
        searchExactCombination(
                boats,
                index + 1,
                targetSeats,
                sum + BoatCapacity.seatsForType(boat.getBoatType()),
                current,
                best);
        current.remove(current.size() - 1);
    }

    private static List<Boat> findMaxSubsetNotExceeding(List<Boat> boats, int targetSeats) {
        List<Boat> best = new ArrayList<>();
        searchMaxSubsetNotExceeding(boats, 0, targetSeats, 0, new ArrayList<>(), best);
        return List.copyOf(best);
    }

    private static void searchMaxSubsetNotExceeding(
            List<Boat> boats,
            int index,
            int targetSeats,
            int sum,
            List<Boat> current,
            List<Boat> best) {
        if (index >= boats.size()) {
            if (isPreferredPartialSubset(current, sum, best, sumSeats(best))) {
                best.clear();
                best.addAll(current);
            }
            return;
        }
        searchMaxSubsetNotExceeding(boats, index + 1, targetSeats, sum, current, best);
        Boat boat = boats.get(index);
        int seats = BoatCapacity.seatsForType(boat.getBoatType());
        if (sum + seats <= targetSeats) {
            current.add(boat);
            searchMaxSubsetNotExceeding(boats, index + 1, targetSeats, sum + seats, current, best);
            current.remove(current.size() - 1);
        }
    }

    private static int sumSeats(List<Boat> boats) {
        return boats.stream().mapToInt(b -> BoatCapacity.seatsForType(b.getBoatType())).sum();
    }

    /** Prefer fewer boats, then lower boat numbers (stable for staff). */
    private static boolean isPreferredBoatSet(List<Boat> candidate, List<Boat> best) {
        if (best.isEmpty()) {
            return true;
        }
        if (candidate.size() != best.size()) {
            return candidate.size() < best.size();
        }
        for (int i = 0; i < candidate.size(); i++) {
            int cmp = BoatNumberComparator.compareNumbers(
                    candidate.get(i).getBoatNumber(), best.get(i).getBoatNumber());
            if (cmp != 0) {
                return cmp < 0;
            }
        }
        return false;
    }

    private static boolean isPreferredPartialSubset(
            List<Boat> candidate, int candidateSum, List<Boat> best, int bestSum) {
        if (candidateSum > bestSum) {
            return true;
        }
        if (candidateSum < bestSum) {
            return false;
        }
        return isPreferredBoatSet(candidate, best);
    }

    private static List<Boat> pickBoats(List<Boat> available, String boatType, int quantity) {
        List<Boat> matches = reserveBoats(available, boatType, quantity);
        if (matches.size() < quantity) {
            return List.of();
        }
        return new ArrayList<>(matches);
    }

    /** Reserve up to {@code quantity} boats of a type (including partial) for queue priority. */
    private static List<Boat> reserveBoats(List<Boat> available, String boatType, int quantity) {
        return available.stream()
                .filter(b -> b.getBoatType().equals(boatType))
                .sorted(Comparator.comparing(Boat::getBoatNumber, BoatNumberComparator::compareNumbers))
                .limit(quantity)
                .toList();
    }

    private static List<Boat> sortedCopy(List<Boat> boats) {
        return boats.stream()
                .sorted(Comparator.comparing(Boat::getBoatNumber, BoatNumberComparator::compareNumbers))
                .toList();
    }

    public static String summarize(RequirementNode requirement) {
        if (requirement instanceof RequirementNode.BoatReq boat) {
            String label = shortType(boat.boatType());
            return boat.quantity() > 1 ? boat.quantity() + "× " + label : label;
        }
        if (requirement instanceof RequirementNode.AndReq and) {
            return and.children().stream()
                    .map(WaitlistMatcher::summarize)
                    .collect(Collectors.joining(" AND "));
        }
        if (requirement instanceof RequirementNode.OrReq or) {
            return or.children().stream()
                    .map(WaitlistMatcher::summarize)
                    .collect(Collectors.joining(" OR "));
        }
        if (requirement instanceof RequirementNode.PartyReq party) {
            String base = "Anything for " + party.partySize();
            if (party.excludeTypes().isEmpty()) {
                return base;
            }
            String excluded = party.excludeTypes().stream()
                    .map(WaitlistMatcher::shortType)
                    .collect(Collectors.joining(", "));
            return base + " (not " + excluded + ")";
        }
        return "";
    }

    private static String shortType(String boatType) {
        if (boatType.startsWith("Kayak")) {
            return "kayak";
        }
        if (boatType.startsWith("Canoe")) {
            return "canoe";
        }
        if (boatType.startsWith("Pedal")) {
            return "pedal boat";
        }
        if (boatType.startsWith("Double kayak")) {
            return "tandem";
        }
        if (boatType.startsWith("Stand-up")) {
            return "SUP";
        }
        return boatType;
    }
}
