package com.skriptvariables.commands;

import com.skriptvariables.SkriptVariables;
import com.skriptvariables.events.VariablesApplyEvent;
import com.skriptvariables.profiler.Profiler;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.util.ApiClient;
import com.skriptvariables.util.SessionUploader;
import com.skriptvariables.util.VariableApplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public final class EditorCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "skriptvariables.editor";
    private static final String PERM_PROFILE = "skriptvariables.profile";
    private static final int DEFAULT_SECONDS = 60;
    private static final int MAX_SECONDS = 600;

    private static final Component PREFIX =
        Component.text("[").color(NamedTextColor.GRAY)
        .append(Component.text("skript-variables").color(NamedTextColor.GOLD))
        .append(Component.text("] ").color(NamedTextColor.GRAY));

    private final SkriptVariables plugin;
    private BukkitTask profileAutoStopTask;

    public EditorCommand(SkriptVariables plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        boolean isProfileCmd = args[0].equalsIgnoreCase("profile");
        if (!sender.hasPermission(PERM) && !sender.isOp()
                && !(isProfileCmd && sender.hasPermission(PERM_PROFILE))) {
            sender.sendMessage(err("You don't have permission to use this command."));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "editor" -> runEditor(sender);
            case "profile" -> runProfile(sender, args);
            case "apply" -> {
                if (args.length < 3) {
                    sender.sendMessage(err("Usage: /skv apply <sessionId> <code> [--force]"));
                    return true;
                }
                boolean force = args.length >= 4 && args[3].equalsIgnoreCase("--force");
                runApply(sender, args[1], args[2], force);
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void runEditor(CommandSender sender) {
        File csvFile = new File("plugins/Skript/variables.csv");
        if (!csvFile.exists()) {
            sender.sendMessage(err("variables.csv not found at: " + csvFile.getPath()));
            return;
        }
        sender.sendMessage(msg("Reading variables, please wait..."));
        async(() -> {
            try {
                ensureRegistered();
                SessionUploader.UploadResult upload;
                try {
                    upload = SessionUploader.upload(csvFile);
                } catch (ApiClient.AuthException e) {
                    reRegister();
                    upload = SessionUploader.upload(csvFile);
                }

                String profileJson = Profiler.lastProfile();
                if (profileJson != null && !profileJson.isBlank()) {
                    try {
                        try {
                            ApiClient.uploadProfile(upload.sessionId(), profileJson);
                        } catch (ApiClient.AuthException e) {
                            reRegister();
                            ApiClient.uploadProfile(upload.sessionId(), profileJson);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "/skv editor profile attach failed", e);
                    }
                }

                String url = ApiClient.siteUrl() + "/editor/" + upload.sessionId();
                final SessionUploader.UploadResult finalUpload = upload;
                sync(() -> {
                    sender.sendMessage(msg("Editor ready! " + finalUpload.totalVars() + " variables loaded."));
                    sender.sendMessage(
                        Component.text("Open Editor ↗")
                            .color(NamedTextColor.YELLOW)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(url))
                    );
                    sender.sendMessage(Component.text(url).color(NamedTextColor.GRAY));
                });
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "/skv editor failed", e);
                sync(() -> sender.sendMessage(err("Failed to open editor: " + friendlyError(e))));
                sync(() -> sender.sendMessage(msg("Full details written to the server console.")));
            }
        });
    }

    private void runProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_PROFILE)) {
            sender.sendMessage(err("You don't have permission to profile."));
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "start" -> profileStart(sender, args);
            case "stop" -> profileStop(sender);
            case "upload" -> profileUpload(sender);
            default -> profileStatus(sender);
        }
    }

    private void profileStart(CommandSender sender, String[] args) {
        if (profileAutoStopTask != null) {
            profileAutoStopTask.cancel();
            profileAutoStopTask = null;
        }
        int seconds = DEFAULT_SECONDS;
        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(err("Duration must be a whole number of seconds."));
                return;
            }
        }
        if (seconds < 1 || seconds > MAX_SECONDS) {
            sender.sendMessage(err("Duration must be between 1 and " + MAX_SECONDS + " seconds."));
            return;
        }
        try {
            Profiler.start();
        } catch (ProfilerUnavailableException e) {
            sender.sendMessage(err(e.getMessage()));
            return;
        }
        sender.sendMessage(msg("Recording for " + seconds + "s. Stop early with /skv profile stop."));

        final int delayTicks = seconds * 20;
        profileAutoStopTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            profileAutoStopTask = null;
            if (Profiler.isRecording()) profileStop(sender);
        }, delayTicks);
    }

    private void profileStop(CommandSender sender) {
        if (profileAutoStopTask != null) {
            profileAutoStopTask.cancel();
            profileAutoStopTask = null;
        }
        if (!Profiler.isRecording()) {
            sender.sendMessage(err("Not recording. Start with /skv profile start."));
            return;
        }
        final String json;
        try {
            json = Profiler.stop();
        } catch (ProfilerUnavailableException e) {
            sender.sendMessage(err(e.getMessage()));
            return;
        }
        sender.sendMessage(msg("Recording stopped. Uploading..."));
        uploadProfileData(sender, json, "/skv profile stop");
    }

    private void profileUpload(CommandSender sender) {
        if (Profiler.isRecording()) {
            sender.sendMessage(err("Recording in progress. Run /skv profile stop first, or wait for it to finish."));
            return;
        }
        String json = Profiler.lastProfile();
        if (json == null || json.isBlank()) {
            sender.sendMessage(err("No profile recorded yet. Run /skv profile start first."));
            return;
        }
        uploadProfileData(sender, json, "/skv profile upload");
    }

    private void uploadProfileData(CommandSender sender, String json, String source) {
        File csvFile = new File("plugins/Skript/variables.csv");
        if (!csvFile.exists()) {
            sender.sendMessage(err("variables.csv not found at: " + csvFile.getPath()));
            return;
        }
        async(() -> {
            try {
                ensureRegistered();
                SessionUploader.UploadResult upload;
                try {
                    upload = SessionUploader.upload(csvFile);
                } catch (ApiClient.AuthException e) {
                    reRegister();
                    upload = SessionUploader.upload(csvFile);
                }
                try {
                    ApiClient.uploadProfile(upload.sessionId(), json);
                } catch (ApiClient.AuthException e) {
                    reRegister();
                    ApiClient.uploadProfile(upload.sessionId(), json);
                }
                String url = ApiClient.siteUrl() + "/editor/" + upload.sessionId() + "?tab=prof";
                sync(() -> {
                    sender.sendMessage(msg("Profile ready."));
                    sender.sendMessage(
                        Component.text("Open Profiler ↗")
                            .color(NamedTextColor.YELLOW)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(url))
                    );
                });
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, source + " failed", e);
                sync(() -> sender.sendMessage(err("Failed to upload profile: " + friendlyError(e) + " Run /skv profile upload to retry.")));
            }
        });
    }

    private void profileStatus(CommandSender sender) {
        if (!Profiler.isRecording()) {
            sender.sendMessage(msg("Not recording."));
            return;
        }
        sender.sendMessage(msg(
            "Recording for " + (Profiler.elapsedMs() / 1000) + "s, "
                + Profiler.executions() + " trigger executions so far."));
    }

    private void runApply(CommandSender sender, String sessionId, String applyCode, boolean force) {
        sender.sendMessage(msg("Fetching changes..."));
        async(() -> {
            try {
                String diffJson = ApiClient.getDiff(sessionId, applyCode, force);
                String[] names = VariableApplier.parseNames(diffJson).toArray(new String[0]);
                sync(() -> {
                    VariablesApplyEvent applyEvent = new VariablesApplyEvent(sender, names);
                    plugin.getServer().getPluginManager().callEvent(applyEvent);
                    if (applyEvent.isCancelled()) {
                        sender.sendMessage(msg("Apply cancelled."));
                        return;
                    }
                    VariableApplier.ApplyResult result = VariableApplier.apply(diffJson);
                    sender.sendMessage(msg("Applied " + result.applied() + " change(s), skipped " + result.skipped() + "."));
                    for (String e : result.errors()) {
                        sender.sendMessage(PREFIX.append(Component.text("⚠ " + e).color(NamedTextColor.YELLOW)));
                    }
                    if (force) {
                        sender.sendMessage(
                            Component.text("  ⚠ --force: code is still active and can be re-applied.")
                                .color(NamedTextColor.YELLOW)
                        );
                    }
                });
            } catch (ApiClient.NotFoundException e) {
                sync(() -> sender.sendMessage(err("Code not found or expired. Generate a new one from the editor.")));
            } catch (ApiClient.AuthException e) {
                sync(() -> sender.sendMessage(err("This server's API key is missing or no longer valid. Run /skv editor to re-register, then generate a new apply code.")));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "/skv apply failed", e);
                sync(() -> sender.sendMessage(err("Failed to apply changes: " + friendlyError(e))));
            }
        });
    }

    private void ensureRegistered() throws Exception {
        if (ApiClient.hasApiKey()) return;
        reRegister();
    }

    private void reRegister() throws Exception {
        String key = ApiClient.register();
        plugin.getConfig().set("api-key", key);
        plugin.saveConfig();
        ApiClient.setApiKey(key);
    }

    private void async(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void sync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
            Component.text("skript-variables ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            .append(Component.text("v" + plugin.getPluginMeta().getVersion()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false))
        );
        sender.sendMessage(help("/skv editor", "upload variables and open the editor"));
        sender.sendMessage(help("/skv apply <sessionId> <code> [--force]", "apply changes; --force keeps the code reusable"));
        sender.sendMessage(help("/skv help", "show this help"));
    }

    private static Component help(String cmd, String desc) {
        return Component.text("  " + cmd + " ").color(NamedTextColor.WHITE)
            .append(Component.text(desc).color(NamedTextColor.GRAY));
    }

    private static Component msg(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.WHITE));
    }

    private static Component err(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.RED));
    }

    private static String friendlyError(Exception e) {
        if (e instanceof ApiClient.NetworkException) return e.getMessage();
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1)
            return List.of("editor", "apply", "profile", "help").stream()
                .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                .toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("profile"))
            return List.of("start", "stop", "status", "upload").stream()
                .filter(sub -> sub.startsWith(args[1].toLowerCase()))
                .toList();
        if (args.length == 4 && args[0].equalsIgnoreCase("apply"))
            return List.of("--force").stream()
                .filter(f -> f.startsWith(args[3].toLowerCase()))
                .toList();
        return List.of();
    }
}
