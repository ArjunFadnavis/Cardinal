package com.park.boatrental.waitlist;

import com.park.boatrental.model.Boat;
import com.park.boatrental.util.BoatCapacity;
import com.park.boatrental.util.BoatNumberComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Matches available boats to a party using age/weight rules. Party categories are mutually
 * exclusive (each guest is counted in one box only).
 * <ul>
 *   <li>Under 16 must share a boat with an adult</li>
 *   <li>16–18 may paddle solo or share a tandem with another 16–18; not with a younger child</li>
 *   <li>Canoe: 2 people (adult + partner, two adults, or two teens); 3 people with at least one
 *       adult and at least one under 90 lbs (under-50 box counts); 4 people with at least one adult,
 *       at least two under 50 lbs, and any other guests</li>
 * </ul>
 */
public final class PartyCompositionMatcher {

    private PartyCompositionMatcher() {
    }

    public static Optional<List<Boat>> tryMatch(RequirementNode.PartyReq party, List<Boat> available) {
        PartyPools pools = PartyPools.from(party);
        if (!pools.isStructurallyValid()) {
            return Optional.empty();
        }
        List<Boat> eligible = available.stream()
                .filter(b -> !party.excludeTypes().contains(b.getBoatType()))
                .sorted(Comparator.comparing(Boat::getBoatNumber, BoatNumberComparator::compareNumbers))
                .toList();
        List<Boat> picked = new ArrayList<>();
        if (search(eligible, 0, pools, picked)) {
            return Optional.of(List.copyOf(picked));
        }
        return Optional.empty();
    }

    public static List<Boat> boatsClaimedWhenUnsatisfied(RequirementNode.PartyReq party, List<Boat> available) {
        if (tryMatch(party, available).isPresent()) {
            return List.of();
        }
        PartyPools pools = PartyPools.from(party);
        if (!pools.isStructurallyValid()) {
            return List.of();
        }
        List<Boat> eligible = available.stream()
                .filter(b -> !party.excludeTypes().contains(b.getBoatType()))
                .sorted(Comparator.comparing(Boat::getBoatNumber, BoatNumberComparator::compareNumbers))
                .toList();
        List<Boat> claimed = new ArrayList<>();
        for (Boat boat : eligible) {
            if (pools.isEmpty()) {
                break;
            }
            for (PartyPools delta : patternsFor(boat)) {
                if (pools.canSubtract(delta)) {
                    pools.subtract(delta);
                    claimed.add(boat);
                    break;
                }
            }
        }
        return claimed;
    }

    private static boolean search(List<Boat> boats, int index, PartyPools pools, List<Boat> picked) {
        if (pools.isEmpty()) {
            return true;
        }
        if (index >= boats.size()) {
            return false;
        }
        if (search(boats, index + 1, pools, picked)) {
            return true;
        }
        Boat boat = boats.get(index);
        for (PartyPools delta : patternsFor(boat)) {
            PartyPools next = pools.copy();
            if (!next.canSubtract(delta)) {
                continue;
            }
            next.subtract(delta);
            picked.add(boat);
            if (search(boats, index + 1, next, picked)) {
                return true;
            }
            picked.remove(picked.size() - 1);
        }
        return false;
    }

    private static List<PartyPools> patternsFor(Boat boat) {
        return switch (boatKind(boat.getBoatType())) {
            case CANOE -> canoePatterns();
            case SINGLE -> singlePatterns();
            case TANDEM -> tandemPatterns();
            case MULTI -> multiPatterns(BoatCapacity.seatsForType(boat.getBoatType()));
        };
    }

    private static List<PartyPools> canoePatterns() {
        List<PartyPools> patterns = new ArrayList<>();
        for (int people = 2; people <= 4; people++) {
            buildCanoeGroups(people, new PartyPools(), patterns);
        }
        return patterns;
    }

    private static void buildCanoeGroups(int remaining, PartyPools group, List<PartyPools> out) {
        if (remaining == 0) {
            if (isValidCanoeGroup(group)) {
                out.add(copy(group));
            }
            return;
        }
        tryAddCanoe(group, out, remaining, p -> {
            p.adults++;
            return true;
        });
        tryAddCanoe(group, out, remaining, p -> {
            p.childrenAge16to18++;
            return true;
        });
        tryAddCanoe(group, out, remaining, p -> {
            p.childrenUnder16++;
            return p.adults > 0;
        });
        tryAddCanoe(group, out, remaining, p -> {
            p.childrenUnder90Lbs++;
            return p.adults > 0;
        });
        tryAddCanoe(group, out, remaining, p -> {
            p.childrenUnder50Lbs++;
            return p.adults > 0;
        });
    }

    private static void tryAddCanoe(
            PartyPools group,
            List<PartyPools> out,
            int remaining,
            java.util.function.Function<PartyPools, Boolean> add) {
        PartyPools next = copy(group);
        if (!add.apply(next)) {
            return;
        }
        buildCanoeGroups(remaining - 1, next, out);
    }

    private static boolean isValidCanoeGroup(PartyPools group) {
        int people = group.total();
        if (people < 2 || people > 4) {
            return false;
        }
        if (group.childrenUnder16 > 0 && group.adults < 1) {
            return false;
        }
        if (people == 2) {
            return isValidTwoPersonCanoe(group);
        }
        if (people == 3) {
            return group.adults >= 1
                    && group.childrenUnder90Lbs + group.childrenUnder50Lbs >= 1;
        }
        return group.adults >= 1 && group.childrenUnder50Lbs >= 2;
    }

    private static boolean isValidTwoPersonCanoe(PartyPools group) {
        if (group.adults == 2) {
            return true;
        }
        if (group.childrenAge16to18 == 2) {
            return true;
        }
        if (group.adults != 1) {
            return false;
        }
        return group.childrenUnder16 == 1
                || group.childrenUnder90Lbs == 1
                || group.childrenUnder50Lbs == 1
                || group.childrenAge16to18 == 1;
    }

    private static List<PartyPools> singlePatterns() {
        List<PartyPools> patterns = new ArrayList<>();
        patterns.add(delta(1, 0, 0, 0, 0));
        patterns.add(delta(0, 0, 0, 1, 0));
        return patterns;
    }

    private static List<PartyPools> tandemPatterns() {
        List<PartyPools> patterns = new ArrayList<>();
        patterns.add(delta(2, 0, 0, 0, 0));
        patterns.add(delta(0, 0, 0, 2, 0));
        patterns.add(delta(1, 0, 0, 1, 0));
        patterns.add(delta(1, 1, 0, 0, 0));
        return patterns;
    }

    private static List<PartyPools> multiPatterns(int seats) {
        List<PartyPools> patterns = new ArrayList<>();
        int max = seats;
        for (int people = 1; people <= max; people++) {
            buildGroups(people, new PartyPools(), patterns);
        }
        return patterns;
    }

    private static void buildGroups(int remaining, PartyPools group, List<PartyPools> out) {
        if (remaining == 0) {
            if (isValidNonCanoeGroup(group)) {
                out.add(copy(group));
            }
            return;
        }
        tryAdd(group, out, remaining, p -> {
            p.adults++;
            return true;
        });
        tryAdd(group, out, remaining, p -> {
            if (p.childrenAge16to18 > 0 && p.childrenUnder16 > 0) {
                return false;
            }
            p.childrenAge16to18++;
            return true;
        });
        tryAdd(group, out, remaining, p -> {
            p.childrenUnder16++;
            return p.adults > 0;
        });
    }

    private static void tryAdd(
            PartyPools group,
            List<PartyPools> out,
            int remaining,
            java.util.function.Function<PartyPools, Boolean> add) {
        PartyPools next = copy(group);
        if (!add.apply(next)) {
            return;
        }
        buildGroups(remaining - 1, next, out);
    }

    private static boolean isValidNonCanoeGroup(PartyPools group) {
        if (group.childrenUnder16 > 0 && group.adults < 1) {
            return false;
        }
        if (group.childrenAge16to18 > 0 && group.childrenUnder16 > 0) {
            return false;
        }
        if (group.childrenUnder90Lbs > 0 || group.childrenUnder50Lbs > 0) {
            return false;
        }
        return group.total() > 0;
    }

    private static PartyPools delta(int adults, int under16, int under90, int teens, int under50) {
        PartyPools pools = new PartyPools();
        pools.adults = adults;
        pools.childrenUnder16 = under16;
        pools.childrenUnder90Lbs = under90;
        pools.childrenAge16to18 = teens;
        pools.childrenUnder50Lbs = under50;
        return pools;
    }

    private static PartyPools copy(PartyPools source) {
        return source.copy();
    }

    private enum BoatKind {
        CANOE,
        SINGLE,
        TANDEM,
        MULTI
    }

    private static BoatKind boatKind(String boatType) {
        if (boatType.startsWith("Canoe")) {
            return BoatKind.CANOE;
        }
        if (boatType.startsWith("Double kayak")) {
            return BoatKind.TANDEM;
        }
        if (boatType.startsWith("Kayak (1") || boatType.startsWith("Stand-up")) {
            return BoatKind.SINGLE;
        }
        return BoatKind.MULTI;
    }
}
