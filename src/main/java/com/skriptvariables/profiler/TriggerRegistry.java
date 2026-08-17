package com.skriptvariables.profiler;

import ch.njol.skript.SkriptEventHandler;
import ch.njol.skript.lang.Trigger;
import com.google.common.collect.Multimap;
import org.bukkit.event.Event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Reads and rewrites Skript's private registry of event triggers. */
public final class TriggerRegistry {

    private TriggerRegistry() {}

    @SuppressWarnings("unchecked")
    public static Multimap<Class<? extends Event>, Trigger> access() throws ProfilerUnavailableException {
        try {
            Field field = SkriptEventHandler.class.getDeclaredField("triggers");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Multimap)) {
                throw new ProfilerUnavailableException("Skript's trigger registry is not the expected type");
            }
            return (Multimap<Class<? extends Event>, Trigger>) value;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ProfilerUnavailableException("Could not read Skript's trigger registry: " + e.getMessage());
        }
    }

    /**
     * Replaces every value using the supplied mapper, preserving keys and per-key
     * ordering. Rewrites one key at a time via {@link Multimap#replaceValues} rather
     * than clearing the whole map, so the registry is never globally empty; the
     * window where the registry is inconsistent shrinks to a single key.
     */
    public static void rewrite(Multimap<Class<? extends Event>, Trigger> map,
                               java.util.function.Function<Trigger, Trigger> mapper) {
        List<Class<? extends Event>> keys = new ArrayList<>(map.keySet());
        for (Class<? extends Event> key : keys) {
            List<Trigger> mapped = new ArrayList<>();
            for (Trigger trigger : map.get(key)) {
                mapped.add(mapper.apply(trigger));
            }
            map.replaceValues(key, mapped);
        }
    }
}
