package com.park.boatrental.waitlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequirementNode.BoatReq.class, name = "BOAT"),
        @JsonSubTypes.Type(value = RequirementNode.AndReq.class, name = "AND"),
        @JsonSubTypes.Type(value = RequirementNode.OrReq.class, name = "OR"),
        @JsonSubTypes.Type(value = RequirementNode.PartyReq.class, name = "PARTY")
})
public sealed interface RequirementNode permits RequirementNode.BoatReq, RequirementNode.AndReq,
        RequirementNode.OrReq, RequirementNode.PartyReq {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BoatReq(String type, String boatType, int quantity) implements RequirementNode {
        public BoatReq {
            if (type == null) {
                type = "BOAT";
            }
            if (quantity < 1) {
                quantity = 1;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AndReq(String type, List<RequirementNode> children) implements RequirementNode {
        public AndReq {
            if (type == null) {
                type = "AND";
            }
            if (children == null) {
                children = new ArrayList<>();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrReq(String type, List<RequirementNode> children) implements RequirementNode {
        public OrReq {
            if (type == null) {
                type = "OR";
            }
            if (children == null) {
                children = new ArrayList<>();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartyReq(
            String type,
            Integer partySize,
            int adults,
            int childrenUnder16,
            int childrenUnder90Lbs,
            int childrenAge16to18,
            int childrenUnder50Lbs,
            List<String> excludeTypes) implements RequirementNode {

        public PartyReq {
            if (type == null) {
                type = "PARTY";
            }
            if (excludeTypes == null) {
                excludeTypes = List.of();
            }
        }

        public int totalPeople() {
            int total = adults + childrenUnder16 + childrenUnder90Lbs + childrenAge16to18 + childrenUnder50Lbs;
            if (total > 0) {
                return total;
            }
            return partySize != null ? partySize : 0;
        }
    }
}
