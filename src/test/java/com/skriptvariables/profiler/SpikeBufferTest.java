package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpikeBufferTest {

    @Test
    void keepsOnlyTheSlowestUpToCapacity() {
        SpikeBuffer buffer = new SpikeBuffer(3);
        buffer.offer(1, 100L, 500L);
        buffer.offer(2, 101L, 900L);
        buffer.offer(3, 102L, 100L);
        buffer.offer(4, 103L, 700L);

        List<Spike> spikes = buffer.drainSorted();

        assertEquals(3, spikes.size());
        assertEquals(900L, spikes.get(0).ns());
        assertEquals(700L, spikes.get(1).ns());
        assertEquals(500L, spikes.get(2).ns());
    }

    @Test
    void returnsEverythingWhenUnderCapacity() {
        SpikeBuffer buffer = new SpikeBuffer(10);
        buffer.offer(1, 100L, 200L);
        buffer.offer(2, 101L, 400L);

        List<Spike> spikes = buffer.drainSorted();

        assertEquals(2, spikes.size());
        assertEquals(400L, spikes.get(0).ns());
        assertEquals(2, spikes.get(0).triggerId());
    }

    @Test
    void emptyBufferReturnsEmptyList() {
        assertEquals(List.of(), new SpikeBuffer(5).drainSorted());
    }
}
