package com.park.boatrental.waitlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RequirementJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RequirementJson() {
    }

    public static String write(RequirementNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid requirement", e);
        }
    }

    public static RequirementNode read(String json) {
        try {
            return MAPPER.readValue(json, RequirementNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid requirement JSON", e);
        }
    }
}
