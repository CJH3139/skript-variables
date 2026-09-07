package com.skriptvariables.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import com.skriptvariables.SkriptVariables;
import com.skriptvariables.service.EditorService;
import com.skriptvariables.service.ProfileDurations;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EffStartProfile extends Effect {

    private @Nullable Expression<Timespan> duration;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        duration = (Expression<Timespan>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        int seconds = EditorService.DEFAULT_PROFILE_SECONDS;
        if (duration != null) {
            Timespan span = duration.getSingle(event);
            if (span == null) return;
            seconds = ProfileDurations.seconds(span.getAs(Timespan.TimePeriod.MILLISECOND));
        }
        EditorService service = SkriptVariables.getService();
        if (service == null) return;
        service.profileStart(Bukkit.getConsoleSender(), seconds);
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        return "start skript profile" + (duration == null ? "" : " for " + duration.toString(event, debug));
    }
}
