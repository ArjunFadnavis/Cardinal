package com.park.boatrental.waitlist;

/**
 * Mutable headcounts for party-composition waitlist matching.
 * Each person is counted in exactly one category. A child in the under-50 box also counts
 * toward canoe “under 90 lbs” rules (e.g. 2 adults + 1 under-50 = 3 on a canoe).
 */
public final class PartyPools {

    public int adults;
    public int childrenUnder16;
    public int childrenUnder90Lbs;
    public int childrenAge16to18;
    public int childrenUnder50Lbs;

    public static PartyPools from(RequirementNode.PartyReq party) {
        PartyPools pools = new PartyPools();
        pools.adults = Math.max(0, party.adults());
        pools.childrenUnder16 = Math.max(0, party.childrenUnder16());
        pools.childrenUnder90Lbs = Math.max(0, party.childrenUnder90Lbs());
        pools.childrenAge16to18 = Math.max(0, party.childrenAge16to18());
        pools.childrenUnder50Lbs = Math.max(0, party.childrenUnder50Lbs());

        if (pools.total() == 0 && party.partySize() != null && party.partySize() > 0) {
            pools.adults = party.partySize();
        }
        return pools;
    }

    public int legacyPartySize() {
        return total();
    }

    /** Total people in the party (one per category box). */
    public int total() {
        return adults + childrenUnder16 + childrenUnder90Lbs + childrenAge16to18 + childrenUnder50Lbs;
    }

    public boolean isEmpty() {
        return total() == 0;
    }

    public boolean isStructurallyValid() {
        if (total() < 1) {
            return false;
        }
        if ((childrenUnder16 > 0 || childrenUnder90Lbs > 0 || childrenUnder50Lbs > 0) && adults < 1) {
            return false;
        }
        return true;
    }

    public PartyPools copy() {
        PartyPools copy = new PartyPools();
        copy.adults = adults;
        copy.childrenUnder16 = childrenUnder16;
        copy.childrenUnder90Lbs = childrenUnder90Lbs;
        copy.childrenAge16to18 = childrenAge16to18;
        copy.childrenUnder50Lbs = childrenUnder50Lbs;
        return copy;
    }

    public boolean canSubtract(PartyPools delta) {
        return adults >= delta.adults
                && childrenUnder16 >= delta.childrenUnder16
                && childrenUnder90Lbs >= delta.childrenUnder90Lbs
                && childrenAge16to18 >= delta.childrenAge16to18
                && childrenUnder50Lbs >= delta.childrenUnder50Lbs;
    }

    public void subtract(PartyPools delta) {
        adults -= delta.adults;
        childrenUnder16 -= delta.childrenUnder16;
        childrenUnder90Lbs -= delta.childrenUnder90Lbs;
        childrenAge16to18 -= delta.childrenAge16to18;
        childrenUnder50Lbs -= delta.childrenUnder50Lbs;
    }
}
