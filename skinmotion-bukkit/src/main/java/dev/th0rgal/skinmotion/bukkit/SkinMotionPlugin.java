package dev.th0rgal.skinmotion.bukkit;

import dev.th0rgal.skinmotion.bukkit.commands.SkinCommand;
import dev.th0rgal.skinmotion.bukkit.storage.SkinStorage;
import dev.th0rgal.skinmotion.core.api.SkinApiClient;
import dev.th0rgal.skinmotion.core.config.Config;
import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.websocket.SkinWebSocketClient;
import dev.th0rgal.skinmotion.core.websocket.WsMessage;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for the SkinMotion Bukkit/Paper plugin.
 * Manages animated skins via WebSocket connection to the API.
 * Persists skins locally using SQLite for offline support.
 */
public final class SkinMotionPlugin extends JavaPlugin implements Listener {

    private static SkinMotionPlugin instance;

    private Config config;
    private SkinApiClient skinApiClient;
    private BukkitAudiences audiences;
    private SkinApplierBukkit skinApplier;
    private SkinAnimationTask animationTask;
    private SkinWebSocketClient webSocketClient;
    private SkinStorage skinStorage;

    /** Cache of player skin configs (in-memory) */
    private final Map<UUID, SkinConfig> playerSkins = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize SQLite storage
        skinStorage = new SkinStorage(getDataFolder(), getLogger());
        try {
            skinStorage.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize skin storage: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize API client
        skinApiClient = new SkinApiClient(
                config.getApiUrl(),
                config.getPluginApiKey(),
                config.getTimeoutSeconds(),
                getLogger()
        );

        // Initialize Adventure audiences for messaging
        audiences = BukkitAudiences.create(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBukkit(this);

        // Register commands
        SkinCommand skinCommand = new SkinCommand(this);
        if (getCommand("skin") != null) {
            getCommand("skin").setExecutor(skinCommand);
            getCommand("skin").setTabCompleter(skinCommand);
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize bStats metrics
        new Metrics(this, 23456);

        // Start animation task
        animationTask = new SkinAnimationTask(this);
        animationTask.start();

        // Connect to WebSocket
        connectWebSocket();

        getLogger().info("SkinMotion enabled! API: " + config.getApiUrl());
    }

    /**
     * Connect to the API WebSocket for real-time updates.
     */
    private void connectWebSocket() {
        if (config.getPluginApiKey().isEmpty()) {
            getLogger().warning("No plugin_api_key configured. WebSocket disabled.");
            return;
        }

        try {
            String wsUrl = config.getApiUrl()
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    + "/ws/plugin";

            webSocketClient = new SkinWebSocketClient(
                    new URI(wsUrl),
                    config.getPluginApiKey(),
                    getLogger(),
                    this::handleWebSocketMessage
            );
            webSocketClient.connectAsync();
            getLogger().info("WebSocket connecting to: " + wsUrl);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize WebSocket: " + e.getMessage());
        }
    }

    /**
     * Handle incoming WebSocket messages.
     */
    private void handleWebSocketMessage(WsMessage message) {
        switch (message.getType()) {
            case SKIN_UPDATED -> {
                String uuid = message.getString("minecraft_uuid");
                String username = message.getString("minecraft_username");
                if (uuid != null) {
                    getLogger().info("Received skin update for " + username + " (" + uuid + ")");
                    // Find online player and refresh their skin
                    getServer().getScheduler().runTask(this, () -> {
                        Player player = Bukkit.getPlayer(UUID.fromString(uuid));
                        if (player != null && player.isOnline()) {
                            refreshPlayerSkin(player);
                        }
                    });
                }
            }
            case CONFIG_CHANGED -> {
                String uuid = message.getString("minecraft_uuid");
                if (uuid != null) {
                    getLogger().info("Received config change for " + uuid);
                    // Update the cached config
                    getServer().getScheduler().runTask(this, () -> {
                        Player player = Bukkit.getPlayer(UUID.fromString(uuid));
                        if (player != null && player.isOnline()) {
                            // Just refresh the whole config
                            refreshPlayerSkin(player);
                        }
                    });
                }
            }
            case PONG -> {
                // Heartbeat response, ignore
            }
            default -> {
                // Ignore other messages
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Notify API that player is online
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendPlayerOnline(
                    playerId.toString(),
                    player.getName()
            );
        }

        // Load skin - first try local storage, then fetch from API if needed
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            // Try loading from local SQLite first
            SkinConfig localSkin = skinStorage.loadSkin(playerId);
            
            if (localSkin != null) {
                // Use locally persisted skin
                playerSkins.put(playerId, localSkin);
                getLogger().info("Loaded persisted skin for " + player.getName() +
                        " (" + localSkin.getFrameCount() + " frames)");

                // Apply first frame to player
                if (localSkin.getFirstFrame() != null) {
                    getServer().getScheduler().runTask(this, () -> {
                        if (player.isOnline()) {
                            skinApplier.applySkin(player, localSkin.getFirstFrame().toSkinProperty());
                        }
                    });
                }
                
                // Still fetch from API in background to check for updates
                fetchAndUpdateSkinFromApi(player, false);
            } else {
                // No local skin, fetch from API
                fetchAndUpdateSkinFromApi(player, true);
            }

            // Generate and send dashboard link if configured
            if (config.isSendLinkOnJoin()) {
                sendDashboardLink(player);
            }
        });
    }

    /**
     * Fetch skin from API and optionally apply immediately.
     */
    private void fetchAndUpdateSkinFromApi(Player player, boolean applyImmediately) {
        skinApiClient.getSkinConfig(player.getUniqueId().toString())
                .thenAccept(apiSkin -> {
                    if (apiSkin != null) {
                        UUID playerId = player.getUniqueId();
                        
                        // Check if skin has changed (compare frame count and first frame)
                        SkinConfig currentSkin = playerSkins.get(playerId);
                        boolean skinChanged = currentSkin == null ||
                                currentSkin.getFrameCount() != apiSkin.getFrameCount() ||
                                !isSameFirstFrame(currentSkin, apiSkin);

                        if (skinChanged || applyImmediately) {
                            playerSkins.put(playerId, apiSkin);
                            
                            // Persist to local storage
                            skinStorage.saveSkin(apiSkin);
                            
                            getLogger().info((applyImmediately ? "Fetched" : "Updated") + 
                                    " skin for " + player.getName() +
                                    " (" + apiSkin.getFrameCount() + " frames)");

                            // Apply first frame to player
                            if (apiSkin.getFirstFrame() != null && (applyImmediately || skinChanged)) {
                                getServer().getScheduler().runTask(this, () -> {
                                    if (player.isOnline()) {
                                        skinApplier.applySkin(player, apiSkin.getFirstFrame().toSkinProperty());
                                    }
                                });
                            }
                        }
                    } else if (applyImmediately) {
                        // API returned no skin - if we had one locally, it was deleted
                        SkinConfig currentSkin = playerSkins.remove(player.getUniqueId());
                        if (currentSkin != null) {
                            skinStorage.deleteSkin(player.getUniqueId());
                        }
                    }
                })
                .exceptionally(e -> {
                    if (applyImmediately) {
                        getLogger().warning("Failed to fetch skin for " + player.getName() + 
                                ": " + e.getMessage());
                    }
                    return null;
                });
    }

    /**
     * Check if two skin configs have the same first frame signature.
     */
    private boolean isSameFirstFrame(SkinConfig a, SkinConfig b) {
        if (a == null || b == null) return false;
        var frameA = a.getFirstFrame();
        var frameB = b.getFirstFrame();
        if (frameA == null || frameB == null) return frameA == frameB;
        return frameA.getTextureSignature().equals(frameB.getTextureSignature());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerSkins.remove(player.getUniqueId());

        // Notify API that player is offline
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendPlayerOffline(player.getUniqueId().toString());
        }
    }

    /**
     * Send dashboard link to a player.
     */
    public void sendDashboardLink(Player player) {
        skinApiClient.generateToken(
                player.getUniqueId().toString(),
                player.getName(),
                config.getServerId()
        ).thenAccept(response -> {
            getServer().getScheduler().runTask(this, () -> {
                if (!player.isOnline()) return;
                
                audiences.player(player).sendMessage(
                        Component.text()
                                .append(Component.text("[SkinMotion] ", NamedTextColor.GREEN))
                                .append(Component.text("Click here to customize your skin: ", NamedTextColor.GRAY))
                                .append(Component.text(response.dashboard_url, NamedTextColor.AQUA)
                                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(response.dashboard_url))
                                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                                Component.text("Click to open dashboard", NamedTextColor.YELLOW))))
                                .build()
                );
            });
        }).exceptionally(e -> {
            getLogger().warning("Failed to generate dashboard link for " + player.getName() + ": " + e.getMessage());
            return null;
        });
    }

    /**
     * Refresh a player's skin from the API.
     * Called when WebSocket notifies us of a skin update.
     */
    public void refreshPlayerSkin(Player player) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            skinApiClient.getSkinConfig(player.getUniqueId().toString())
                    .thenAccept(skinConfig -> {
                        if (skinConfig != null) {
                            playerSkins.put(player.getUniqueId(), skinConfig);
                            
                            // Persist to local storage
                            skinStorage.saveSkin(skinConfig);
                            
                            getLogger().info("Refreshed skin config for " + player.getName() +
                                    " (" + skinConfig.getFrameCount() + " frames)");

                            // Apply first frame to player
                            if (skinConfig.getFirstFrame() != null) {
                                getServer().getScheduler().runTask(this, () -> {
                                    if (!player.isOnline()) return;
                                    
                                    skinApplier.applySkin(player, skinConfig.getFirstFrame().toSkinProperty());
                                    audiences.player(player).sendMessage(
                                            Component.text("Your skin has been updated!", NamedTextColor.GREEN)
                                    );
                                });
                            }

                            // Reset animation state
                            if (animationTask != null) {
                                animationTask.resetPlayer(player.getUniqueId());
                            }
                        } else {
                            // Skin was deleted, restore original
                            getServer().getScheduler().runTask(this, () -> {
                                if (!player.isOnline()) return;
                                
                                playerSkins.remove(player.getUniqueId());
                                skinStorage.deleteSkin(player.getUniqueId());
                                skinApplier.restoreOriginalSkin(player);
                                audiences.player(player).sendMessage(
                                        Component.text("Your custom skin has been removed.", NamedTextColor.YELLOW)
                                );
                            });
                        }
                    })
                    .exceptionally(e -> {
                        getLogger().warning("Failed to refresh skin for " + player.getName() + ": " + e.getMessage());
                        return null;
                    });
        });
    }

    @Override
    public void onDisable() {
        // Disconnect WebSocket
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        if (animationTask != null) {
            animationTask.stop();
            animationTask = null;
        }

        if (audiences != null) {
            audiences.close();
        }

        // Close SQLite storage
        if (skinStorage != null) {
            skinStorage.close();
            skinStorage = null;
        }

        playerSkins.clear();
        instance = null;
        getLogger().info("SkinMotion disabled.");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reload() {
        config = Config.load(getDataFolder());
        skinApiClient = new SkinApiClient(
                config.getApiUrl(),
                config.getPluginApiKey(),
                config.getTimeoutSeconds(),
                getLogger()
        );

        // Reconnect WebSocket
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        connectWebSocket();

        getLogger().info("Configuration reloaded.");
    }

    // Getters

    @NotNull
    public static SkinMotionPlugin get() {
        return instance;
    }

    @NotNull
    public Config getPluginConfig() {
        return config;
    }

    @NotNull
    public SkinApiClient getSkinApiClient() {
        return skinApiClient;
    }

    @NotNull
    public BukkitAudiences getAudiences() {
        return audiences;
    }

    @NotNull
    public SkinApplierBukkit getSkinApplier() {
        return skinApplier;
    }

    @Nullable
    public SkinConfig getPlayerSkinConfig(UUID playerId) {
        return playerSkins.get(playerId);
    }

    public Map<UUID, SkinConfig> getPlayerSkins() {
        return playerSkins;
    }

    /**
     * Check if WebSocket is connected.
     */
    public boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    /**
     * Check if a player has a persisted skin in local storage.
     */
    public boolean hasPersistedSkin(UUID playerId) {
        return skinStorage != null && skinStorage.hasSkin(playerId);
    }
}
