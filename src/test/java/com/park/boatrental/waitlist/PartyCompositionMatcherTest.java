package com.park.boatrental.waitlist;

import com.park.boatrental.model.Boat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyCompositionMatcherTest {

    @Test
    void annFamilyDoesNotBlockJohnKayakWhenOnlySingleReturns() {
        RequirementNode.PartyReq ann = new RequirementNode.PartyReq(
                "PARTY", 2, 1, 1, 0, 0, 0, List.of());
        RequirementNode.BoatReq john = new RequirementNode.BoatReq("BOAT", "Kayak (1 person)", 1);
        Boat s4 = kayak("S4", 4L);

        assertTrue(
                PartyCompositionMatcher.boatsClaimedWhenUnsatisfied(ann, List.of(s4)).isEmpty(),
                "Ann cannot use a solo kayak with a child under 16");
        assertTrue(WaitlistMatcher.boatsClaimedWhenUnsatisfied(ann, List.of(s4)).isEmpty());
        assertTrue(WaitlistMatcher.tryMatch(john, List.of(s4)).isPresent());
    }

    @Test
    void singleWaitlistCanUseKayakNotBlockedByEarlierFamilyParty() {
        RequirementNode.PartyReq family = new RequirementNode.PartyReq(
                "PARTY", null, 1, 2, 0, 0, 0, List.of());
        RequirementNode.BoatReq solo = new RequirementNode.BoatReq("BOAT", "Kayak (1 person)", 1);
        Boat single = kayak("S1", 1L);

        Set<Long> familyClaims = WaitlistMatcher.boatsClaimedWhenUnsatisfied(family, List.of(single));
        assertTrue(familyClaims.isEmpty());

        assertTrue(WaitlistMatcher.tryMatch(solo, List.of(single)).isPresent());
    }

    @Test
    void unsatisfiedPartyMayStillClaimBoatThatLeavesValidRemainder() {
        RequirementNode.PartyReq party = new RequirementNode.PartyReq(
                "PARTY", null, 2, 0, 0, 0, 0, List.of());
        Boat single = kayak("S1", 1L);

        List<Boat> claimed = PartyCompositionMatcher.boatsClaimedWhenUnsatisfied(party, List.of(single));

        assertFalse(claimed.isEmpty(), "one adult can take the single while another adult waits");
    }

    private static Boat kayak(String number, long id) {
        Boat boat = new Boat();
        boat.setId(id);
        boat.setBoatNumber(number);
        boat.setBoatType("Kayak (1 person)");
        return boat;
    }
}
