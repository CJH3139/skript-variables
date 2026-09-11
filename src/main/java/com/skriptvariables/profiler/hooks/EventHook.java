package com.skriptvariables.profiler.hooks;

import ch.njol.skript.lang.Trigger;
import com.google.common.collect.Multimap;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.TriggerKind;
import com.skriptvariables.profiler.TriggerRegistry;
import org.bukkit.event.Event;

import java.util.function.Function;

public final class EventHook implements TriggerHook {

    @Override
    public TriggerKind kind() {
        return TriggerKind.EVENT;
    }

    @Override
    public String label(Trigger trigger) {
        return trigger.getName();
    }

    @Override
    public void wrap(Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        Multimap<Class<? extends Event>, Trigger> map = TriggerRegistry.access();
        TriggerRegistry.rewrite(map, mapper);
    }

    @Override
    public void unwrap() throws ProfilerUnavailableException {
        Multimap<Class<? extends Event>, Trigger> map = TriggerRegistry.access();
        TriggerRegistry.rewrite(map, TriggerFields::unwrapOne);
    }
}
