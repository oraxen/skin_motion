package dev.th0rgal.skinmotion.bukkit.commands;

import dev.th0rgal.skinmotion.bukkit.SkinMotionPlugin;
import net.kyori.adventure.text.Component;
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
 */
public class SkinCommand implements CommandExecutor, TabCompleter {

    private final SkinMotionPlugin plugin;

    public SkinCommand(SkinMotionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "link" -> handleLink(player);
            case "refresh" -> handleRefresh(player);
            case "status" -> handleStatus(player);
            case "reload" -> handleReload(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void handleLink(Player player) {
        if (!player.hasPermission("skinmotion.link")) {
            sendNoPermission(player);
            return;
        }
        plugin.getAudiences().player(player).sendMessage(
                Component.text("Generating dashboard link...", NamedTextColor.GRAY)
        );
        plugin.sendDashboardLink(player);
    }

    private void handleRefresh(Player player) {
        if (!player.hasPermission("skinmotion.refresh")) {
            sendNoPermission(player);
            return;
        }
        plugin.getAudiences().player(player).sendMessage(
                Component.text("Refreshing your skin...", NamedTextColor.GRAY)
        );
        plugin.refreshPlayerSkin(player);
    }

    private void handleStatus(Player player) {
        if (!player.hasPermission("skinmotion.status")) {
            sendNoPermission(player);
            return;
        }
        boolean wsConnected = plugin.isWebSocketConnected();
        var skinConfig = plugin.getPlayerSkinConfig(player.getUniqueId());
        boolean hasPersisted = plugin.hasPersistedSkin(player.getUniqueId());

        plugin.getAudiences().player(player).sendMessage(
                Component.text()
                        .append(Component.text("SkinMotion Status", NamedTextColor.GREEN))
                        .append(Component.newline())
                        .append(Component.text("WebSocket: ", NamedTextColor.GRAY))
                        .append(wsConnected
                                ? Component.text("Connected ✓", NamedTextColor.GREEN)
                                : Component.text("Disconnected ✗", NamedTextColor.RED))
                        .append(Component.newline())
                        .append(Component.text("SkinMotion: ", NamedTextColor.GRAY))
                        .append(skinConfig != null
                                ? Component.text("Active (" + skinConfig.getFrameCount() + " frames)", NamedTextColor.GREEN)
                                : Component.text("None", NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(Component.text("Persisted: ", NamedTextColor.GRAY))
                        .append(hasPersisted
                                ? Component.text("Yes ✓", NamedTextColor.GREEN)
                                : Component.text("No", NamedTextColor.YELLOW))
                        .build()
        );
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("skinmotion.reload")) {
            sendNoPermission(player);
            return;
        }

        plugin.reload();
        plugin.getAudiences().player(player).sendMessage(
                Component.text("Configuration reloaded.", NamedTextColor.GREEN)
        );
    }

    private void sendNoPermission(Player player) {
        plugin.getAudiences().player(player).sendMessage(
                Component.text("You don't have permission to use this command.", NamedTextColor.RED)
        );
    }

    private void showHelp(Player player) {
        var builder = Component.text()
                .append(Component.text("SkinMotion Commands:", NamedTextColor.GREEN));

        if (player.hasPermission("skinmotion.link")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin link", NamedTextColor.AQUA))
                    .append(Component.text(" - Get dashboard link to customize your skin", NamedTextColor.GRAY));
        }
        if (player.hasPermission("skinmotion.refresh")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin refresh", NamedTextColor.AQUA))
                    .append(Component.text(" - Manually refresh your skin", NamedTextColor.GRAY));
        }
        if (player.hasPermission("skinmotion.status")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin status", NamedTextColor.AQUA))
                    .append(Component.text(" - Check connection status", NamedTextColor.GRAY));
        }
        if (player.hasPermission("skinmotion.reload")) {
            builder.append(Component.newline())
                    .append(Component.text("/skin reload", NamedTextColor.AQUA))
                    .append(Component.text(" - Reload plugin configuration", NamedTextColor.GRAY));
        }

        plugin.getAudiences().player(player).sendMessage(builder.build());
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
