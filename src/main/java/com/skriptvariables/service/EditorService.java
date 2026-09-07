package com.skriptvariables.service;

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
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.logging.Level;

public final class EditorService {

    public static final int DEFAULT_PROFILE_SECONDS = 60;
    public static final int MAX_PROFILE_SECONDS = 600;
    private static final int PREVIEW_LINES = 15;
    private static final int PREVIEW_VALUE_WIDTH = 40;

    public static final Component PREFIX =
        Component.text("[").color(NamedTextColor.GRAY)
        .append(Component.text("skript-variables").color(NamedTextColor.GOLD))
        .append(Component.text("] ").color(NamedTextColor.GRAY));

    private final SkriptVariables plugin;
    private BukkitTask profileAutoStopTask;

    public EditorService(SkriptVariables plugin) {
        this.plugin = plugin;
    }

    public void openEditor(CommandSender sender) {
        File csvFile = locateCsv();
        sender.sendMessage(msg("Reading variables, please wait..."));
        async(() -> {
            try {
                SessionUploader.UploadResult upload = uploadSession(csvFile);

                String profileJson = Profiler.lastProfile();
                if (profileJson != null && !profileJson.isBlank()) {
                    try {
                        uploadProfile(upload.sessionId(), profileJson);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "/skv editor profile attach failed", e);
                    }
                }

                String url = ApiClient.siteUrl() + "/editor/" + upload.sessionId();
                sync(() -> {
                    sender.sendMessage(msg("Editor ready! " + upload.totalVars() + " variables loaded."));
                    sender.sendMessage(link("Open Editor ↗", url));
                    sender.sendMessage(Component.text(url).color(NamedTextColor.GRAY));
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "/skv editor failed", e);
                sync(() -> sender.sendMessage(err("Failed to open editor: " + friendlyError(e))));
                sync(() -> sender.sendMessage(msg("Full details written to the server console.")));
            }
        });
    }

    public void apply(CommandSender sender, String sessionId, String applyCode, boolean force) {
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
            } catch (Exception e) {
                reportApplyFailure(sender, "/skv apply", e);
            }
        });
    }

    public void previewApply(CommandSender sender, String sessionId, String applyCode) {
        sender.sendMessage(msg("Fetching changes..."));
        async(() -> {
            try {
                String diffJson = ApiClient.getDiff(sessionId, applyCode, true);
                sync(() -> {
                    VariableApplier.PreviewResult preview = VariableApplier.preview(diffJson);
                    sendPreview(sender, preview, sessionId, applyCode);
                });
            } catch (Exception e) {
                reportApplyFailure(sender, "/skv apply --preview", e);
            }
        });
    }

    private void sendPreview(CommandSender sender, VariableApplier.PreviewResult preview,
                             String sessionId, String applyCode) {
        int total = preview.sets().size() + preview.deletes().size() + preview.unparseable().size();
        sender.sendMessage(msg("Preview: " + preview.sets().size() + " to set, "
            + preview.deletes().size() + " to delete, "
            + preview.unparseable().size() + " would be skipped. Nothing changed."));

        int shown = 0;
        for (VariableApplier.Change c : preview.sets()) {
            if (shown++ >= PREVIEW_LINES) break;
            sender.sendMessage(Component.text("  " + c.name() + "  ").color(NamedTextColor.WHITE)
                .append(Component.text(c.type() + "  ").color(NamedTextColor.GRAY))
                .append(Component.text(clip(c.value())).color(NamedTextColor.AQUA)));
        }
        for (VariableApplier.Change c : preview.deletes()) {
            if (shown++ >= PREVIEW_LINES) break;
            sender.sendMessage(Component.text("  - " + c.name() + "  ").color(NamedTextColor.WHITE)
                .append(Component.text("(delete)").color(NamedTextColor.GRAY)));
        }
        for (VariableApplier.Change c : preview.unparseable()) {
            if (shown++ >= PREVIEW_LINES) break;
            sender.sendMessage(Component.text("  ⚠ " + c.name() + "  " + c.type() + "  " + clip(c.value())
                + "  (cannot parse, would be skipped)").color(NamedTextColor.YELLOW));
        }
        if (total > PREVIEW_LINES) {
            sender.sendMessage(Component.text("  ... and " + (total - PREVIEW_LINES) + " more").color(NamedTextColor.GRAY));
        }
        if (total > 0) {
            sender.sendMessage(msg("Run /skv apply " + sessionId + " " + applyCode + " to apply."));
        }
    }

    private void reportApplyFailure(CommandSender sender, String source, Exception e) {
        if (e instanceof ApiClient.NotFoundException) {
            sync(() -> sender.sendMessage(err("Code not found or expired. Generate a new one from the editor.")));
        } else if (e instanceof ApiClient.AuthException) {
            sync(() -> sender.sendMessage(err("This server's API key is missing or no longer valid. Run /skv editor to re-register, then generate a new apply code.")));
        } else {
            plugin.getLogger().log(Level.WARNING, source + " failed", e);
            sync(() -> sender.sendMessage(err("Failed to fetch changes: " + friendlyError(e))));
        }
    }

    public boolean profileStart(CommandSender sender, int seconds) {
        if (seconds < 1 || seconds > MAX_PROFILE_SECONDS) {
            sender.sendMessage(err("Duration must be between 1 and " + MAX_PROFILE_SECONDS + " seconds."));
            return false;
        }
        cancelAutoStop();
        try {
            Profiler.start();
        } catch (ProfilerUnavailableException e) {
            sender.sendMessage(err(e.getMessage()));
            return false;
        }
        sender.sendMessage(msg("Recording for " + seconds + "s. Stop early with /skv profile stop."));

        final int delayTicks = seconds * 20;
        profileAutoStopTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            profileAutoStopTask = null;
            if (Profiler.isRecording()) profileStop(sender);
        }, delayTicks);
        return true;
    }

    public boolean profileStop(CommandSender sender) {
        cancelAutoStop();
        if (!Profiler.isRecording()) {
            sender.sendMessage(err("Not recording. Start with /skv profile start."));
            return false;
        }
        final String json;
        try {
            json = Profiler.stop();
        } catch (ProfilerUnavailableException e) {
            sender.sendMessage(err(e.getMessage()));
            return false;
        }
        sender.sendMessage(msg("Recording stopped. Uploading..."));
        uploadProfileData(sender, json, "/skv profile stop");
        return true;
    }

    public void profileUpload(CommandSender sender) {
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

    public void profileStatus(CommandSender sender) {
        if (!Profiler.isRecording()) {
            sender.sendMessage(msg("Not recording."));
            return;
        }
        sender.sendMessage(msg(
            "Recording for " + (Profiler.elapsedMs() / 1000) + "s, "
                + Profiler.executions() + " trigger executions so far."));
    }

    private void cancelAutoStop() {
        if (profileAutoStopTask != null) {
            profileAutoStopTask.cancel();
            profileAutoStopTask = null;
        }
    }

    private void uploadProfileData(CommandSender sender, String json, String source) {
        File csvFile = locateCsv();
        async(() -> {
            try {
                SessionUploader.UploadResult upload = uploadSession(csvFile);
                uploadProfile(upload.sessionId(), json);
                String url = ApiClient.siteUrl() + "/editor/" + upload.sessionId() + "?tab=prof";
                sync(() -> {
                    sender.sendMessage(msg("Profile ready."));
                    sender.sendMessage(link("Open Profiler ↗", url));
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, source + " failed", e);
                sync(() -> sender.sendMessage(err("Failed to upload profile: " + friendlyError(e) + " Run /skv profile upload to retry.")));
            }
        });
    }

    private File locateCsv() {
        File csvFile = new File("plugins/Skript/variables.csv");
        if (csvFile.isFile()) return csvFile;
        plugin.getLogger().fine("variables.csv not found, reading variables from memory only.");
        return null;
    }

    private SessionUploader.UploadResult uploadSession(File csvFile) throws Exception {
        ensureRegistered();
        try {
            return SessionUploader.upload(csvFile);
        } catch (ApiClient.AuthException e) {
            reRegister();
            return SessionUploader.upload(csvFile);
        }
    }

    private void uploadProfile(String sessionId, String json) throws Exception {
        try {
            ApiClient.uploadProfile(sessionId, json);
        } catch (ApiClient.AuthException e) {
            reRegister();
            ApiClient.uploadProfile(sessionId, json);
        }
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

    private static Component link(String label, String url) {
        return Component.text(label)
            .color(NamedTextColor.YELLOW)
            .decorate(TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl(url));
    }

    private static String clip(String s) {
        if (s == null) return "";
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() <= PREVIEW_VALUE_WIDTH ? oneLine : oneLine.substring(0, PREVIEW_VALUE_WIDTH - 1) + "…";
    }

    public static Component msg(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.WHITE));
    }

    public static Component err(String text) {
        return PREFIX.append(Component.text(text).color(NamedTextColor.RED));
    }

    private static String friendlyError(Exception e) {
        if (e instanceof ApiClient.NetworkException) return e.getMessage();
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }
}
