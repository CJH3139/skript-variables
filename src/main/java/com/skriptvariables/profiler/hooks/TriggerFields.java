package com.skriptvariables.profiler.hooks;

import ch.njol.skript.lang.Trigger;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.ProfilingTrigger;

import java.lang.reflect.Field;
import java.util.function.Function;

final class TriggerFields {

    private TriggerFields() {}

    static Field find(Class<?> owner, String name) throws ProfilerUnavailableException {
        Class<?> c = owner;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (RuntimeException e) {
                throw new ProfilerUnavailableException("Cannot access " + owner.getSimpleName() + "." + name + ": " + e.getMessage());
            }
        }
        throw new ProfilerUnavailableException("No field " + name + " on " + owner.getSimpleName());
    }

    static Object readStatic(Class<?> owner, String name) throws ProfilerUnavailableException {
        try {
            return find(owner, name).get(null);
        } catch (IllegalAccessException e) {
            throw new ProfilerUnavailableException("Cannot read " + owner.getSimpleName() + "." + name);
        }
    }

    static void swap(Object holder, Field field, Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        try {
            Object current = field.get(holder);
            if (!(current instanceof Trigger trigger)) return;
            Trigger mapped = mapper.apply(trigger);
            if (mapped != trigger) field.set(holder, mapped);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new ProfilerUnavailableException("Cannot swap trigger on " + holder.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static Trigger unwrapOne(Trigger current) {
        return current instanceof ProfilingTrigger wrapped ? wrapped.original() : current;
    }
}
