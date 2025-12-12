package dev.th0rgal.customcapes.bungee.commands;

import dev.th0rgal.customcapes.bungee.CustomCapesBungee;
import dev.th0rgal.customcapes.core.api.SkinApiProvider;
import dev.th0rgal.customcapes.core.config.Config;
import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import dev.th0rgal.customcapes.core.util.SkinUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BungeeCord command handler for /cape.
 */
public final class CapeCommand extends Command implements TabExecutor {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final CustomCapesBungee plugin;

    public CapeCommand(CustomCapesBungee plugin) {
        super("cape", "customcapes.use");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Config config = plugin.getPluginConfig();
        Audience audience = plugin.getAudiences().sender(sender);

        if (!(sender instanceof ProxiedPlayer player)) {
            sendMessage(audience, config.getPrefix() + config.getPlayerOnly());
            return;
        }

        if (!player.hasPermission("customcapes.use")) {
            sendMessage(audience, config.getPrefix() + config.getNoPermission());
            return;
        }

        if (args.length == 0) {
            sendMessage(audience, config.getPrefix() + config.getUsage());
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(player, audience, config);
            case "clear" -> handleClear(player, audience, config);
            case "reload" -> handleReload(player, audience, config);
            default -> handleApply(player, audience, config, subCommand);
        }
    }

    private void handleList(ProxiedPlayer player, Audience audience, Config config) {
        sendMessage(audience, config.getPrefix() + config.getListHeader());
        
        for (CapeType capeType : CapeType.values()) {
            String entry = config.getListEntry()
                .replace("%cape%", capeType.getId() + " <gray>(" + capeType.getDisplayName() + ")");
            sendMessage(audience, entry);
        }
    }

    private void handleClear(ProxiedPlayer player, Audience audience, Config config) {
        boolean restored = plugin.getSkinApplier().restoreOriginalSkin(player);
        
        if (restored) {
            sendMessage(audience, config.getPrefix() + config.getCapeCleared());
        } else {
            sendMessage(audience, config.getPrefix() + "<yellow>No cape to remove.");
        }
    }

    private void handleReload(ProxiedPlayer player, Audience audience, Config config) {
        if (!player.hasPermission("customcapes.admin")) {
            sendMessage(audience, config.getPrefix() + config.getNoPermission());
            return;
        }

        plugin.reload();
        sendMessage(audience, config.getPrefix() + "<green>Configuration reloaded.");
    }

    private void handleApply(ProxiedPlayer player, Audience audience, Config config, String capeTypeId) {
        CapeType capeType = CapeType.fromId(capeTypeId);
        if (capeType == null) {
            String message = config.getCapeNotFound().replace("%cape%", capeTypeId);
            sendMessage(audience, config.getPrefix() + message);
            return;
        }

        SkinProperty currentSkin = plugin.getSkinApplier().getSkinProperty(player);
        if (currentSkin == null) {
            sendMessage(audience, config.getPrefix() + 
                "<red>Could not retrieve your current skin. Please try again.");
            return;
        }

        String skinUrl = SkinUtil.extractSkinUrl(currentSkin);
        if (skinUrl == null) {
            sendMessage(audience, config.getPrefix() + 
                "<red>Could not determine your skin URL. Please try again.");
            return;
        }

        SkinVariant variant = SkinUtil.extractVariant(currentSkin);

        sendMessage(audience, config.getPrefix() + config.getApplying());

        SkinApiProvider apiProvider = plugin.getApiProvider();

        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            try {
                TextureData textureData = apiProvider.generate(skinUrl, capeType, variant);
                
                if (!player.isConnected()) {
                    return;
                }

                SkinProperty newSkin = textureData.toSkinProperty();
                plugin.getSkinApplier().applySkin(player, newSkin);
                
                Audience playerAudience = plugin.getAudiences().player(player);
                sendMessage(playerAudience, config.getPrefix() + config.getCapeApplied());
                
                if (textureData.isCached()) {
                    sendMessage(playerAudience, "<gray>(served from cache)");
                }

            } catch (SkinApiProvider.SkinApiException e) {
                if (!player.isConnected()) {
                    return;
                }
                
                Audience playerAudience = plugin.getAudiences().player(player);
                String errorMessage = config.getError().replace("%error%", e.getMessage());
                sendMessage(playerAudience, config.getPrefix() + errorMessage);
                
                plugin.getLogger().warning("Failed to generate cape for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private void sendMessage(Audience audience, String message) {
        audience.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            return List.of();
        }

        if (!sender.hasPermission("customcapes.use")) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            
            completions.add("list");
            completions.add("clear");
            
            if (sender.hasPermission("customcapes.admin")) {
                completions.add("reload");
            }
            
            for (CapeType capeType : CapeType.values()) {
                completions.add(capeType.getId());
            }
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}

