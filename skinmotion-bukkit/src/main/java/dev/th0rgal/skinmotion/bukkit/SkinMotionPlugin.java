package dev.th0rgal.skinmotion.bukkit;

import dev.th0rgal.skinmotion.bukkit.commands.SkinCommand;
import dev.th0rgal.skinmotion.bukkit.message.Audiences;
import dev.th0rgal.skinmotion.bukkit.scheduler.PlatformScheduler;
import dev.th0rgal.skinmotion.bukkit.storage.SkinStorage;
import dev.th0rgal.skinmotion.core.api.SkinApiClient;
import dev.th0rgal.skinmotion.core.config.Config;
import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.model.SkinFrame;
import dev.th0rgal.skinmotion.core.websocket.SkinWebSocketClient;
import dev.th0rgal.skinmotion.core.websocket.WsMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for the SkinMotion Bukkit/Paper plugin.
 * Manages animated skins via WebSocket connection to the API.
 * Persists skins locally using SQLite for offline support.
 */
public final class SkinMotionPlugin extends JavaPlugin implements Listener {

    private static SkinMotionPlugin instance;

    private volatile Config config;
    private volatile SkinApiClient skinApiClient;
    private PlatformScheduler scheduler;
    private Audiences audiences;
    private SkinApplierBukkit skinApplier;
    private SkinAnimationTask animationTask;
    private volatile SkinWebSocketClient webSocketClient;
    private volatile SkinStorage skinStorage;

    /** Cache of player skin configs (in-memory) */
    private final Map<UUID, SkinConfig> playerSkins = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        scheduler = new PlatformScheduler(this);

        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize SQLite storage
        skinStorage = new SkinStorage(getDataFolder(), getLogger());
        try {
            skinStorage.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize skin storage: " + e.getMessage());
            skinStorage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize API client
        skinApiClient = createApiClient();

        // Initialize Adventure audiences for messaging
        audiences = new Audiences(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBukkit(this, scheduler);

        // Register commands
        SkinCommand skinCommand = new SkinCommand(this);
        PluginCommand command = getCommand("skin");
        if (command != null) {
            command.setExecutor(skinCommand);
            command.setTabCompleter(skinCommand);
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Start animation task
        animationTask = new SkinAnimationTask(this, scheduler);
        animationTask.start();

        // Connect to WebSocket
        connectWebSocket();

        if (PlatformScheduler.isFolia()) {
            getLogger().info("Folia detected - using region schedulers");
        }
        getLogger().info("SkinMotion enabled! API: " + config.getApiUrl());
    }

    private SkinApiClient createApiClient() {
        return new SkinApiClient(
                config.getApiUrl(),
                config.getPluginApiKey(),
                config.getTimeoutSeconds(),
                getLogger()
        );
    }

    /**
     * Connect to the API WebSocket for real-time updates.
     */
    private void connectWebSocket() {
        if (!hasApiKey()) {
            getLogger().warning("No plugin_api_key configured. WebSocket disabled.");
            return;
        }

        try {
            String apiUrl = config.getApiUrl().endsWith("/")
                    ? config.getApiUrl().substring(0, config.getApiUrl().length() - 1)
                    : config.getApiUrl();
            String wsUrl = apiUrl
                    .replaceFirst("^https://", "wss://")
                    .replaceFirst("^http://", "ws://")
                    + "/ws/plugin";

            SkinWebSocketClient client = new SkinWebSocketClient(
                    new URI(wsUrl),
                    config.getPluginApiKey(),
                    getLogger(),
                    this::handleWebSocketMessage,
                    this::onWebSocketConnected
            );
            webSocketClient = client;
            client.connectAsync();
            getLogger().info("WebSocket connecting to: " + wsUrl);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize WebSocket: " + e.getMessage());
        }
    }

    /**
     * Re-announce online players and catch up on updates missed while disconnected.
     */
    private void onWebSocketConnected() {
        scheduler.runGlobal(() -> {
            SkinWebSocketClient client = webSocketClient;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (client != null) {
                    client.sendPlayerOnline(player.getUniqueId().toString(), player.getName());
                }
                syncSkin(player, false);
            }
        });
    }

    /**
     * Handle incoming WebSocket messages.
     */
    private void handleWebSocketMessage(WsMessage message) {
        switch (message.getType()) {
            case SKIN_UPDATED, CONFIG_CHANGED -> {
                UUID playerId = parseUuid(message.getString("minecraft_uuid"));
                if (playerId == null) {
                    return;
                }
                getLogger().info("Received " + message.getType().getJsonType() + " for " + playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    syncSkin(player, true);
                }
            }
            default -> {
                // Pong and other messages need no handling
            }
        }
    }

    @Nullable
    private static UUID parseUuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Notify API that player is online
        SkinWebSocketClient client = webSocketClient;
        if (client != null) {
            client.sendPlayerOnline(playerId.toString(), player.getName());
        }

        // Load skin - first try local storage, then fetch from API in the background
        scheduler.runAsync(() -> {
            SkinStorage storage = skinStorage;
            SkinConfig localSkin = storage != null ? storage.loadSkin(playerId) : null;

            if (localSkin != null && cacheSkin(player, localSkin)) {
                getLogger().info("Loaded persisted skin for " + player.getName() +
                        " (" + localSkin.getFrameCount() + " frames)");
                applyFirstFrame(player, localSkin);
            }

            // Still fetch from API to check for updates
            syncSkin(player, false);

            // Generate and send dashboard link if configured
            if (config.isSendLinkOnJoin() && hasApiKey()) {
                sendDashboardLink(player);
            }
        });
    }

    /**
     * Cache a skin for an online player.
     *
     * @return false if the player left in the meantime (nothing is cached)
     */
    private boolean cacheSkin(Player player, SkinConfig skin) {
        UUID playerId = player.getUniqueId();
        playerSkins.put(playerId, skin);
        if (!player.isOnline()) {
            // Quit raced with this async load: don't leak the entry
            playerSkins.remove(playerId);
            return false;
        }
        return true;
    }

    private void applyFirstFrame(Player player, SkinConfig skin) {
        SkinFrame firstFrame = skin.getFirstFrame();
        if (firstFrame != null) {
            skinApplier.applySkin(player, firstFrame.toSkinProperty());
        }
    }

    /**
     * Fetch a player's skin from the API, then cache, persist and apply it.
     * A skin deleted on the dashboard restores the player's original skin.
     * API errors keep the current skin untouched.
     *
     * @param notify Whether to tell the player about the outcome
     */
    public void syncSkin(@NotNull Player player, boolean notify) {
        UUID playerId = player.getUniqueId();
        skinApiClient.getSkinConfig(playerId.toString())
                .thenAccept(apiSkin -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    SkinConfig currentSkin = playerSkins.get(playerId);
                    if (apiSkin == null) {
                        removeSkin(player, notify);
                        return;
                    }

                    if (apiSkin.hasSameContent(currentSkin)) {
                        if (notify) {
                            send(player, Component.text("Your skin is already up to date.", NamedTextColor.GREEN));
                        }
                        return;
                    }

                    if (!cacheSkin(player, apiSkin)) {
                        return;
                    }
                    SkinStorage storage = skinStorage;
                    if (storage != null) {
                        storage.saveSkin(apiSkin);
                    }
                    if (animationTask != null) {
                        animationTask.resetPlayer(playerId);
                    }

                    getLogger().info("Synced skin for " + player.getName() +
                            " (" + apiSkin.getFrameCount() + " frames)");
                    applyFirstFrame(player, apiSkin);
                    if (notify) {
                        send(player, Component.text("Your skin has been updated!", NamedTextColor.GREEN));
                    }
                })
                .exceptionally(e -> {
                    Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                    getLogger().warning("Failed to fetch skin for " + player.getName() + ": " + cause.getMessage());
                    if (notify) {
                        send(player, Component.text("Could not reach the skin service, try again later.",
                                NamedTextColor.RED));
                    }
                    return null;
                });
    }

    private void removeSkin(Player player, boolean notify) {
        UUID playerId = player.getUniqueId();
        SkinConfig removed = playerSkins.remove(playerId);
        SkinStorage storage = skinStorage;
        boolean persisted = storage != null && storage.hasSkin(playerId);
        if (persisted) {
            storage.deleteSkin(playerId);
        }
        if (removed == null && !persisted) {
            if (notify) {
                send(player, Component.text("You don't have a SkinMotion skin yet. Use /skin link to create one.",
                        NamedTextColor.YELLOW));
            }
            return;
        }
        if (animationTask != null) {
            animationTask.resetPlayer(playerId);
        }
        skinApplier.restoreOriginalSkin(player);
        send(player, Component.text("Your custom skin has been removed.", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        playerSkins.remove(playerId);
        if (animationTask != null) {
            animationTask.removePlayer(playerId);
        }
        skinApplier.clearStoredSkin(playerId);

        // Notify API that player is offline
        SkinWebSocketClient client = webSocketClient;
        if (client != null) {
            client.sendPlayerOffline(playerId.toString());
        }
    }

    /**
     * Send dashboard link to a player.
     */
    public void sendDashboardLink(Player player) {
        if (!hasApiKey()) {
            send(player, Component.text("Dashboard links are not configured on this server.", NamedTextColor.RED));
            getLogger().warning("Cannot generate dashboard link: no plugin_api_key configured.");
            return;
        }
        skinApiClient.generateToken(
                player.getUniqueId().toString(),
                player.getName(),
                config.getServerId()
        ).thenAccept(response -> send(player,
                Component.text()
                        .append(Component.text("[SkinMotion] ", NamedTextColor.GREEN))
                        .append(Component.text("Click here to customize your skin: ", NamedTextColor.GRAY))
                        .append(Component.text(response.dashboard_url, NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(response.dashboard_url))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("Click to open dashboard", NamedTextColor.YELLOW))))
                        .build()
        )).exceptionally(e -> {
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            getLogger().warning("Failed to generate dashboard link for " + player.getName() + ": " + cause.getMessage());
            send(player, Component.text("Could not generate a dashboard link, try again later.", NamedTextColor.RED));
            return null;
        });
    }

    /**
     * Send a message to an online player from any thread.
     */
    public void send(@NotNull Player player, @NotNull Component message) {
        scheduler.runForEntity(player, () -> {
            if (player.isOnline()) {
                audiences.sender(player).sendMessage(message);
            }
        });
    }

    /**
     * Reply to a command sender. Must be called from the sender's thread (e.g. a command handler).
     */
    public void reply(@NotNull CommandSender sender, @NotNull Component message) {
        audiences.sender(sender).sendMessage(message);
    }

    @Override
    public void onDisable() {
        // Disconnect WebSocket
        SkinWebSocketClient client = webSocketClient;
        webSocketClient = null;
        if (client != null) {
            client.disconnect();
        }

        if (animationTask != null) {
            animationTask.stop();
            animationTask = null;
        }

        if (scheduler != null) {
            scheduler.cancelAll();
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
        skinApiClient = createApiClient();

        // Reconnect WebSocket
        SkinWebSocketClient client = webSocketClient;
        webSocketClient = null;
        if (client != null) {
            client.disconnect();
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

    public boolean hasApiKey() {
        return !config.getPluginApiKey().isEmpty();
    }

    /**
     * Check if WebSocket is connected.
     */
    public boolean isWebSocketConnected() {
        SkinWebSocketClient client = webSocketClient;
        return client != null && client.isConnected();
    }

    public boolean isAnimationAvailable() {
        return animationTask != null && animationTask.isAvailable();
    }

    /**
     * Check if a player has a persisted skin in local storage.
     */
    public boolean hasPersistedSkin(UUID playerId) {
        SkinStorage storage = skinStorage;
        return storage != null && storage.hasSkin(playerId);
    }
}
