package com.skriptvariables.profiler;

import ch.njol.skript.lang.Trigger;
import org.bukkit.event.Event;

import java.util.ArrayList;

/**
 * Wraps a real Trigger and times its execution. The empty item list handed to
 * the superclass is inert because super.execute is never called.
 */
public final class ProfilingTrigger extends Trigger {

    private final Trigger original;
    private final TriggerStats stats;
    private final SpikeBuffer spikes;

    public ProfilingTrigger(Trigger original, TriggerStats stats, SpikeBuffer spikes) {
        super(original.getScript(), original.getName(), original.getEvent(), new ArrayList<>());
        setDebugLabel(original.getDebugLabel());
        this.original = original;
        this.stats = stats;
        this.spikes = spikes;
    }

    public Trigger original() {
        return original;
    }

    @Override
    public boolean execute(Event event) {
        long start = System.nanoTime();
        try {
            return original.execute(event);
        } finally {
            long took = System.nanoTime() - start;
            stats.record(took);
            spikes.offer(stats.id(), System.currentTimeMillis(), took);
        }
    }
}
