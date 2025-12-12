package dev.th0rgal.customcapes.bukkit.commands;

import dev.th0rgal.customcapes.bukkit.CustomCapesPlugin;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
import dev.th0rgal.customcapes.core.api.CapesListResponse;
import dev.th0rgal.customcapes.core.config.Config;
import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import dev.th0rgal.customcapes.core.util.SkinUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for /cape command.
 */
public final class CapeCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final CustomCapesPlugin plugin;

    public CapeCommand(@NotNull CustomCapesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        Config config = plugin.getPluginConfig();
        Audience audience = plugin.getAudiences().sender(sender);

        // Check if player
        if (!(sender instanceof Player player)) {
            sendMessage(audience, config.getPrefix() + config.getPlayerOnly());
            return true;
        }

        // Check permission
        if (!player.hasPermission("customcapes.use")) {
            sendMessage(audience, config.getPrefix() + config.getNoPermission());
            return true;
        }

        if (args.length == 0) {
            sendMessage(audience, config.getPrefix() + config.getUsage());
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(player, audience, config);
            case "clear" -> handleClear(player, audience, config);
            case "reload" -> handleReload(player, audience, config);
            default -> handleApply(player, audience, config, subCommand);
        }

        return true;
    }

    /**
     * Handle /cape list - show available capes.
     */
    private void handleList(Player player, Audience audience, Config config) {
        List<CapesListResponse.CapeInfo> capes = plugin.getAvailableCapes();

        if (capes == null || capes.isEmpty()) {
            sendMessage(audience, config.getPrefix() + "<yellow>Loading available capes...");
            // Trigger a refresh and try again later
            plugin.refreshAvailableCapes();
            return;
        }

        sendMessage(audience, config.getPrefix() + config.getListHeader());

        for (CapesListResponse.CapeInfo cape : capes) {
            // Only show capes that exist in the CapeType enum to match handleApply
            // validation
            if (!CapeType.isValid(cape.getId())) {
                continue;
            }

            String availability = cape.isAvailable() ? "<green>✓" : "<red>✗";
            String entry = config.getListEntry()
                    .replace("%cape%", cape.getId() + " <gray>(" + cape.getName() + ") " + availability);
            sendMessage(audience, entry);
        }
    }

    /**
     * Handle /cape clear - restore original skin.
     */
    private void handleClear(Player player, Audience audience, Config config) {
        boolean restored = plugin.getSkinApplier().restoreOriginalSkin(player);

        if (restored) {
            sendMessage(audience, config.getPrefix() + config.getCapeCleared());
        } else {
            sendMessage(audience, config.getPrefix() + "<yellow>No cape to remove.");
        }
    }

    /**
     * Handle /cape reload - reload configuration.
     */
    private void handleReload(Player player, Audience audience, Config config) {
        if (!player.hasPermission("customcapes.admin")) {
            sendMessage(audience, config.getPrefix() + config.getNoPermission());
            return;
        }

        plugin.reload();
        sendMessage(audience, config.getPrefix() + "<green>Configuration reloaded.");
    }

    /**
     * Handle /cape <type> - apply a cape.
     */
    private void handleApply(Player player, Audience audience, Config config, String capeTypeId) {
        // Validate cape type
        CapeType capeType = CapeType.fromId(capeTypeId);
        if (capeType == null) {
            String message = config.getCapeNotFound().replace("%cape%", capeTypeId);
            sendMessage(audience, config.getPrefix() + message);
            return;
        }

        // Check if cape is available from the API
        if (!plugin.isCapeAvailable(capeTypeId)) {
            sendMessage(audience, config.getPrefix() +
                    "<red>This cape is not currently available. Use <white>/cape list</white> to see available capes.");
            return;
        }

        // Get current skin
        SkinProperty currentSkin = plugin.getSkinApplier().getCurrentSkin(player);
        if (currentSkin == null) {
            sendMessage(audience, config.getPrefix() +
                    "<red>Could not retrieve your current skin. Please try again.");
            return;
        }

        // Extract skin URL and variant
        String skinUrl = SkinUtil.extractSkinUrl(currentSkin);
        if (skinUrl == null) {
            sendMessage(audience, config.getPrefix() +
                    "<red>Could not determine your skin URL. Please try again.");
            return;
        }

        SkinVariant variant = SkinUtil.extractVariant(currentSkin);

        // Notify player
        sendMessage(audience, config.getPrefix() + config.getApplying());

        // Make async API call
        CapesApiClient apiClient = plugin.getApiClient();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextureData textureData = apiClient.generate(skinUrl, capeType, variant);

                // Apply skin on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    SkinProperty newSkin = textureData.toSkinProperty();
                    plugin.getSkinApplier().applySkin(player, newSkin);

                    Audience playerAudience = plugin.getAudiences().player(player);
                    sendMessage(playerAudience, config.getPrefix() + config.getCapeApplied());

                    if (textureData.isCached()) {
                        sendMessage(playerAudience, "<gray>(served from cache)");
                    }
                });

            } catch (CapesApiClient.CapesApiException e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    Audience playerAudience = plugin.getAudiences().player(player);
                    String errorMessage = config.getError().replace("%error%", e.getMessage());
                    sendMessage(playerAudience, config.getPrefix() + errorMessage);
                });

                plugin.getLogger().warning("Failed to generate cape for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Send a MiniMessage formatted message to an audience.
     */
    private void sendMessage(Audience audience, String message) {
        audience.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (!sender.hasPermission("customcapes.use")) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();

            // Add subcommands
            completions.add("list");
            completions.add("clear");

            if (sender.hasPermission("customcapes.admin")) {
                completions.add("reload");
            }

            // Add only available cape types that also exist in the CapeType enum
            // This prevents suggesting capes that would fail enum validation in handleApply
            List<CapesListResponse.CapeInfo> capes = plugin.getAvailableCapes();
            if (capes != null && !capes.isEmpty()) {
                for (CapesListResponse.CapeInfo cape : capes) {
                    // Only suggest capes that are both available AND valid in the enum
                    if (cape.isAvailable() && CapeType.isValid(cape.getId())) {
                        completions.add(cape.getId());
                    }
                }
            } else {
                // Fallback to all cape types if not yet loaded or empty
                // Also trigger a refresh if capes list is empty
                if (capes != null && capes.isEmpty()) {
                    plugin.refreshAvailableCapes();
                }
                for (CapeType capeType : CapeType.values()) {
                    completions.add(capeType.getId());
                }
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
