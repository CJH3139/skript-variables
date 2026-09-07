package com.skriptvariables.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileDurationsTest {

    @Test
    void wholeSecondsPassThrough() {
        assertEquals(60, ProfileDurations.seconds(60_000L));
    }

    @Test
    void roundsToNearestSecond() {
        assertEquals(2, ProfileDurations.seconds(1_500L));
        assertEquals(1, ProfileDurations.seconds(1_400L));
    }

    @Test
    void subSecondBecomesOneSecond() {
        assertEquals(1, ProfileDurations.seconds(20L));
    }

    @Test
    void zeroAndNegativeStayZeroSoTheServiceRejectsThem() {
        assertEquals(0, ProfileDurations.seconds(0L));
        assertEquals(0, ProfileDurations.seconds(-5L));
    }

    @Test
    void hugeValuesClampToIntRange() {
        assertEquals(Integer.MAX_VALUE, ProfileDurations.seconds(Long.MAX_VALUE));
    }
}
