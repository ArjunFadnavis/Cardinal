package com.park.boatrental.util;

import com.park.boatrental.model.Boat;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoatNumberComparator implements Comparator<Boat> {

    private static final Pattern NUMBER_SUFFIX = Pattern.compile("(\\d+)$");

    public static final BoatNumberComparator INSTANCE = new BoatNumberComparator();

    private BoatNumberComparator() {
    }

    @Override
    public int compare(Boat a, Boat b) {
        int typeCompare = a.getBoatType().compareToIgnoreCase(b.getBoatType());
        if (typeCompare != 0) {
            return typeCompare;
        }
        return compareNumbers(a.getBoatNumber(), b.getBoatNumber());
    }

    public static int compareNumbers(String left, String right) {
        int leftNum = trailingNumber(left);
        int rightNum = trailingNumber(right);
        if (leftNum >= 0 && rightNum >= 0 && leftNum != rightNum) {
            return Integer.compare(leftNum, rightNum);
        }
        return left.compareToIgnoreCase(right);
    }

    private static int trailingNumber(String boatNumber) {
        Matcher matcher = NUMBER_SUFFIX.matcher(boatNumber);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }
}
