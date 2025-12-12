package dev.th0rgal.customcapes.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
import dev.th0rgal.customcapes.core.config.Config;
import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import dev.th0rgal.customcapes.core.util.SkinUtil;
import dev.th0rgal.customcapes.velocity.CustomCapesVelocity;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Velocity command handler for /cape.
 */
public final class CapeCommand implements SimpleCommand {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final CustomCapesVelocity plugin;

    public CapeCommand(CustomCapesVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        Config config = plugin.getPluginConfig();

        if (!(source instanceof Player player)) {
            source.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getPlayerOnly()));
            return;
        }

        if (!player.hasPermission("customcapes.use")) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getNoPermission()));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getUsage()));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(player, config);
            case "clear" -> handleClear(player, config);
            case "reload" -> handleReload(player, config);
            default -> handleApply(player, config, subCommand);
        }
    }

    private void handleList(Player player, Config config) {
        player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getListHeader()));
        
        for (CapeType capeType : CapeType.values()) {
            String entry = config.getListEntry()
                .replace("%cape%", capeType.getId() + " <gray>(" + capeType.getDisplayName() + ")");
            player.sendMessage(MINI_MESSAGE.deserialize(entry));
        }
    }

    private void handleClear(Player player, Config config) {
        boolean restored = plugin.getSkinApplier().restoreOriginalSkin(player);
        
        if (restored) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getCapeCleared()));
        } else {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + "<yellow>No cape to remove."));
        }
    }

    private void handleReload(Player player, Config config) {
        if (!player.hasPermission("customcapes.admin")) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getNoPermission()));
            return;
        }

        plugin.reload();
        player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + "<green>Configuration reloaded."));
    }

    private void handleApply(Player player, Config config, String capeTypeId) {
        CapeType capeType = CapeType.fromId(capeTypeId);
        if (capeType == null) {
            String message = config.getCapeNotFound().replace("%cape%", capeTypeId);
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + message));
            return;
        }

        SkinProperty currentSkin = plugin.getSkinApplier().getSkinProperty(player);
        if (currentSkin == null) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + 
                "<red>Could not retrieve your current skin. Please try again."));
            return;
        }

        String skinUrl = SkinUtil.extractSkinUrl(currentSkin);
        if (skinUrl == null) {
            player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + 
                "<red>Could not determine your skin URL. Please try again."));
            return;
        }

        SkinVariant variant = SkinUtil.extractVariant(currentSkin);

        player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getApplying()));

        CapesApiClient apiClient = plugin.getApiClient();

        // Use async API call
        apiClient.generateAsync(skinUrl, capeType, variant)
            .thenAccept(textureData -> {
                if (!player.isActive()) {
                    return;
                }

                SkinProperty newSkin = textureData.toSkinProperty();
                plugin.getSkinApplier().applySkin(player, newSkin);
                
                player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + config.getCapeApplied()));
                
                if (textureData.isCached()) {
                    player.sendMessage(MINI_MESSAGE.deserialize("<gray>(served from cache)"));
                }
            })
            .exceptionally(throwable -> {
                if (!player.isActive()) {
                    return null;
                }
                
                String errorMessage = config.getError().replace("%error%", throwable.getMessage());
                player.sendMessage(MINI_MESSAGE.deserialize(config.getPrefix() + errorMessage));
                
                plugin.getLogger().warn("Failed to generate cape for {}: {}", 
                    player.getUsername(), throwable.getMessage());
                return null;
            });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("customcapes.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            
            completions.add("list");
            completions.add("clear");
            
            if (invocation.source().hasPermission("customcapes.admin")) {
                completions.add("reload");
            }
            
            for (CapeType capeType : CapeType.values()) {
                completions.add(capeType.getId());
            }
            
            return CompletableFuture.completedFuture(
                completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList())
            );
        }

        return CompletableFuture.completedFuture(List.of());
    }
}

