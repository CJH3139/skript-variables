package com.skriptvariables;

import ch.njol.skript.Skript;
import ch.njol.skript.ScriptLoader;
import com.skriptvariables.commands.EditorCommand;
import com.skriptvariables.skript.EvtVariablesApply;
import com.skriptvariables.util.ApiClient;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;

public final class SkriptVariables extends JavaPlugin {

    private static SkriptVariables instance;
    private static SkriptAddon addon;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ApiClient.setSite(getConfig().getString("api-base", ""));
        String savedKey = getConfig().getString("api-key", "");
        if (!savedKey.isEmpty()) ApiClient.setApiKey(savedKey);

        int pluginId = 31367;
        Metrics metrics = new Metrics(this, pluginId);

        addon = Skript.instance().registerAddon(SkriptVariables.class, "SkriptVariables");
        EvtVariablesApply.register();

        var cmd = getCommand("skv");
        if (cmd != null) {
            var executor = new EditorCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new com.skriptvariables.profiler.ProfilerGuard(), this);

        ScriptLoader.eventRegistry().register(ScriptLoader.ScriptUnloadEvent.class,
            (parser, script) -> com.skriptvariables.profiler.Profiler.abortIfRecording());

        getLogger().info("SkriptVariables enabled. Use /skv editor to open the variable editor.");
    }

    @Override
    public void onDisable() {
        com.skriptvariables.profiler.Profiler.abortIfRecording();
        getLogger().info("SkriptVariables disabled.");
    }

    public static SkriptVariables getInstance() {
        return instance;
    }

    public static SkriptAddon getAddon() {
        return addon;
    }

    public static boolean isOopskPresent() {
        return Bukkit.getServer().getPluginManager().isPluginEnabled("oopsk");
    }
}
