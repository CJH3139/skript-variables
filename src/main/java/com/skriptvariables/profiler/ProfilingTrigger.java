package com.skriptvariables.profiler;

import ch.njol.skript.lang.Trigger;
import org.bukkit.event.Event;

import java.util.ArrayList;

public final class ProfilingTrigger extends Trigger {

    private final Trigger original;
    private final TriggerStats stats;
    private final SpikeBuffer spikes;
    private final CallStack callStack;

    public ProfilingTrigger(Trigger original, TriggerStats stats, SpikeBuffer spikes, CallStack callStack) {
        super(original.getScript(), original.getName(), original.getEvent(), new ArrayList<>());
        setDebugLabel(original.getDebugLabel());
        setLineNumber(original.getLineNumber());
        this.original = original;
        this.stats = stats;
        this.spikes = spikes;
        this.callStack = callStack;
    }

    public Trigger original() {
        return original;
    }

    @Override
    public boolean execute(Event event) {
        callStack.enter();
        long start = System.nanoTime();
        try {
            return original.execute(event);
        } finally {
            CallStack.Timing timing = callStack.exit(System.nanoTime() - start);
            stats.record(timing.inclusiveNs(), timing.selfNs());
            spikes.offer(stats.id(), System.currentTimeMillis(), timing.inclusiveNs());
        }
    }
}
