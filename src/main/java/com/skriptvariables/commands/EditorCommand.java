package com.skriptvariables.commands;

import com.skriptvariables.SkriptVariables;
import com.skriptvariables.service.EditorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.skriptvariables.service.EditorService.err;

public final class EditorCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "skriptvariables.editor";
    private static final String PERM_PROFILE = "skriptvariables.profile";

    private final SkriptVariables plugin;
    private final EditorService service;

    public EditorCommand(SkriptVariables plugin, EditorService service) {
        this.plugin = plugin;
        this.service = service;
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
            case "editor" -> service.openEditor(sender);
            case "profile" -> runProfile(sender, args);
            case "apply" -> runApply(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void runApply(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(err("Usage: /skv apply <sessionId> <code> [--force|--preview]"));
            return;
        }
        String flag = args.length >= 4 ? args[3].toLowerCase() : "";
        switch (flag) {
            case "--preview" -> service.previewApply(sender, args[1], args[2]);
            case "--force" -> service.apply(sender, args[1], args[2], true);
            default -> service.apply(sender, args[1], args[2], false);
        }
    }

    private void runProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_PROFILE)) {
            sender.sendMessage(err("You don't have permission to profile."));
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "start" -> {
                int seconds = EditorService.DEFAULT_PROFILE_SECONDS;
                if (args.length >= 3) {
                    try {
                        seconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(err("Duration must be a whole number of seconds."));
                        return;
                    }
                }
                service.profileStart(sender, seconds);
            }
            case "stop" -> service.profileStop(sender);
            case "upload" -> service.profileUpload(sender);
            default -> service.profileStatus(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
            Component.text("skript-variables ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            .append(Component.text("v" + plugin.getPluginMeta().getVersion()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false))
        );
        sender.sendMessage(help("/skv editor", "upload variables and open the editor"));
        sender.sendMessage(help("/skv apply <sessionId> <code> [--force]", "apply changes; --force keeps the code reusable"));
        sender.sendMessage(help("/skv apply <sessionId> <code> --preview", "show what would change without applying"));
        sender.sendMessage(help("/skv profile start|stop|status|upload", "record and upload Skript timings"));
        sender.sendMessage(help("/skv help", "show this help"));
    }

    private static Component help(String cmd, String desc) {
        return Component.text("  " + cmd + " ").color(NamedTextColor.WHITE)
            .append(Component.text(desc).color(NamedTextColor.GRAY));
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
            return List.of("--force", "--preview").stream()
                .filter(f -> f.startsWith(args[3].toLowerCase()))
                .toList();
        return List.of();
    }
}
