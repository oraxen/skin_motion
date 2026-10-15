package dev.th0rgal.skinmotion.bukkit.commands;

import dev.th0rgal.skinmotion.bukkit.SkinMotionPlugin;
import dev.th0rgal.skinmotion.bukkit.scheduler.PlatformScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command handler for /skin subcommands.
 * {@code status}, {@code reload} and help also work from the console.
 */
public class SkinCommand implements CommandExecutor, TabCompleter {

    private final SkinMotionPlugin plugin;

    public SkinCommand(SkinMotionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "link" -> handleLink(sender);
            case "refresh" -> handleRefresh(sender);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    private void handleLink(CommandSender sender) {
        if (!sender.hasPermission("skinmotion.link")) {
            sendNoPermission(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sendPlayerOnly(sender);
            return;
        }
        plugin.reply(player, Component.text("Generating dashboard link...", NamedTextColor.GRAY));
        plugin.sendDashboardLink(player);
    }

    private void handleRefresh(CommandSender sender) {
        if (!sender.hasPermission("skinmotion.refresh")) {
            sendNoPermission(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sendPlayerOnly(sender);
            return;
        }
        plugin.reply(player, Component.text("Refreshing your skin...", NamedTextColor.GRAY));
        plugin.syncSkin(player, true);
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("skinmotion.status")) {
            sendNoPermission(sender);
            return;
        }
        boolean wsConnected = plugin.isWebSocketConnected();

        TextComponent.Builder builder = Component.text()
                .append(Component.text("SkinMotion Status", NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("WebSocket: ", NamedTextColor.GRAY))
                .append(wsConnected
                        ? Component.text("Connected ✓", NamedTextColor.GREEN)
                        : Component.text(plugin.hasApiKey() ? "Disconnected ✗" : "Disabled (no plugin_api_key)",
                                NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Animation: ", NamedTextColor.GRAY))
                .append(plugin.isAnimationAvailable()
                        ? Component.text("Available", NamedTextColor.GREEN)
                        : Component.text("Unavailable on this server", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Scheduler: ", NamedTextColor.GRAY))
                .append(Component.text(PlatformScheduler.isFolia() ? "Folia regions" : "Bukkit", NamedTextColor.WHITE));

        if (sender instanceof Player player) {
            var skinConfig = plugin.getPlayerSkinConfig(player.getUniqueId());
            boolean hasPersisted = plugin.hasPersistedSkin(player.getUniqueId());
            builder.append(Component.newline())
                    .append(Component.text("SkinMotion: ", NamedTextColor.GRAY))
                    .append(skinConfig != null
                            ? Component.text("Active (" + skinConfig.getFrameCount() + " frames)", NamedTextColor.GREEN)
                            : Component.text("None", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Persisted: ", NamedTextColor.GRAY))
                    .append(hasPersisted
                            ? Component.text("Yes ✓", NamedTextColor.GREEN)
                            : Component.text("No", NamedTextColor.YELLOW));
        } else {
            builder.append(Component.newline())
                    .append(Component.text("Online players with a skin: ", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getPlayerSkins().size(), NamedTextColor.WHITE));
        }

        plugin.reply(sender, builder.build());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("skinmotion.reload")) {
            sendNoPermission(sender);
            return;
        }

        plugin.reload();
        plugin.reply(sender, Component.text("Configuration reloaded.", NamedTextColor.GREEN));
    }

    private void sendNoPermission(CommandSender sender) {
        plugin.reply(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
    }

    private void sendPlayerOnly(CommandSender sender) {
        plugin.reply(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
    }

    private void showHelp(CommandSender sender) {
        var builder = Component.text()
                .append(Component.text("SkinMotion Commands:", NamedTextColor.GREEN));

        if (sender.hasPermission("skinmotion.link")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin link", NamedTextColor.AQUA))
                    .append(Component.text(" - Get dashboard link to customize your skin", NamedTextColor.GRAY));
        }
        if (sender.hasPermission("skinmotion.refresh")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin refresh", NamedTextColor.AQUA))
                    .append(Component.text(" - Manually refresh your skin", NamedTextColor.GRAY));
        }
        if (sender.hasPermission("skinmotion.status")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin status", NamedTextColor.AQUA))
                    .append(Component.text(" - Check connection status", NamedTextColor.GRAY));
        }
        if (sender.hasPermission("skinmotion.reload")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin reload", NamedTextColor.AQUA))
                    .append(Component.text(" - Reload plugin configuration", NamedTextColor.GRAY));
        }

        plugin.reply(sender, builder.build());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();

            if (sender.hasPermission("skinmotion.link") && "link".startsWith(input)) {
                completions.add("link");
            }
            if (sender.hasPermission("skinmotion.refresh") && "refresh".startsWith(input)) {
                completions.add("refresh");
            }
            if (sender.hasPermission("skinmotion.status") && "status".startsWith(input)) {
                completions.add("status");
            }
            if (sender.hasPermission("skinmotion.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }

            return completions;
        }
        return Collections.emptyList();
    }
}
