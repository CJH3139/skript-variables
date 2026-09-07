package com.skriptvariables.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.skriptvariables.SkriptVariables;
import com.skriptvariables.service.EditorService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EffStopProfile extends Effect {

    private @Nullable Expression<CommandSender> recipient;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        recipient = (Expression<CommandSender>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CommandSender sender = recipient == null ? null : recipient.getSingle(event);
        if (sender == null) sender = Bukkit.getConsoleSender();
        EditorService service = SkriptVariables.getService();
        if (service == null) return;
        service.profileStop(sender);
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        return "stop skript profile" + (recipient == null ? "" : " and send link to " + recipient.toString(event, debug));
    }
}
