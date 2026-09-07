package com.skriptvariables.skript;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.skriptvariables.profiler.Profiler;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CondProfilerRecording extends Condition {

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        return Profiler.isRecording() != isNegated();
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        return "skript profiler is " + (isNegated() ? "not " : "") + "recording";
    }
}
