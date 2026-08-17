package com.skriptvariables.profiler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Stops recording before anything can rebuild Skript's triggers underneath us.
 */
public final class ProfilerGuard implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (isReload(event.getCommand())) Profiler.abortIfRecording();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isReload(event.getMessage())) Profiler.abortIfRecording();
    }

    static boolean isReload(String raw) {
        if (raw == null) return false;
        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String[] tokens = cmd.trim().split("\\s+");
        if (tokens.length < 2) return false;
        String first = tokens[0];
        int colon = first.lastIndexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        first = first.toLowerCase();
        String second = tokens[1].toLowerCase();
        return (first.equals("sk") || first.equals("skript"))
            && (second.equals("reload") || second.equals("disable") || second.equals("enable"));
    }
}
