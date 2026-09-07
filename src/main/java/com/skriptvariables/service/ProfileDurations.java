package com.skriptvariables.service;

public final class ProfileDurations {

    private ProfileDurations() {}

    public static int seconds(long millis) {
        if (millis <= 0) return 0;
        long rounded = millis / 1000L + (millis % 1000L >= 500L ? 1 : 0);
        if (rounded < 1) rounded = 1;
        return (int) Math.min(rounded, Integer.MAX_VALUE);
    }
}
