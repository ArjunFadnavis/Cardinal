package com.park.boatrental.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoatCapacity {

    private static final Pattern CAPACITY = Pattern.compile("(\\d+)\\s*person", Pattern.CASE_INSENSITIVE);

    private BoatCapacity() {
    }

    public static int seatsForType(String boatType) {
        Matcher matcher = CAPACITY.matcher(boatType);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }
}
