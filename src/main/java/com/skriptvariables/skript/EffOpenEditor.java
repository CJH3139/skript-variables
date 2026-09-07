package com.skriptvariables.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.skriptvariables.SkriptVariables;
import com.skriptvariables.service.EditorService;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EffOpenEditor extends Effect {

    private Expression<CommandSender> recipients;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        recipients = (Expression<CommandSender>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        EditorService service = SkriptVariables.getService();
        if (service == null) return;
        for (CommandSender sender : recipients.getArray(event)) {
            service.openEditor(sender);
        }
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        return "open variable editor for " + recipients.toString(event, debug);
    }
}
