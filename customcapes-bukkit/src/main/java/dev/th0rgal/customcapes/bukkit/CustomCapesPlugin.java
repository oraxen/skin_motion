package dev.th0rgal.customcapes.bukkit;

import dev.th0rgal.customcapes.bukkit.commands.CapeCommand;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
import dev.th0rgal.customcapes.core.api.CapesListResponse;
import dev.th0rgal.customcapes.core.config.Config;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main entry point for the Custom Capes Bukkit/Paper plugin.
 */
public final class CustomCapesPlugin extends JavaPlugin {

    private static CustomCapesPlugin instance;

    private Config config;
    private CapesApiClient apiClient;
    private BukkitAudiences audiences;
    private SkinApplierBukkit skinApplier;

    /**
     * Container for cape data that must be updated atomically together.
     */
    private static final class CapeData {
        final List<CapesListResponse.CapeInfo> capes;
        final Set<String> availableIds;

        CapeData(List<CapesListResponse.CapeInfo> capes, Set<String> availableIds) {
            this.capes = capes;
            this.availableIds = availableIds;
        }
    }

    /** Atomically updated cape data (null = not yet loaded) */
    private final AtomicReference<CapeData> capeData = new AtomicReference<>(null);

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize API client
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());

        // Initialize Adventure audiences for messaging
        audiences = BukkitAudiences.create(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBukkit(this);

        // Register commands
        CapeCommand capeCommand = new CapeCommand(this);
        getCommand("cape").setExecutor(capeCommand);
        getCommand("cape").setTabCompleter(capeCommand);

        // Initialize bStats metrics
        new Metrics(this, 23456); // Replace with actual bStats plugin ID

        getLogger().info("Custom Capes enabled! API: " + config.getApiUrl());

        // Fetch available capes from API in background
        refreshAvailableCapes();
    }

    /**
     * Refresh the list of available capes from the API.
     */
    public void refreshAvailableCapes() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                CapesListResponse response = apiClient.getAvailableCapes();

                // Build new set of available cape IDs
                Set<String> newAvailableIds = ConcurrentHashMap.newKeySet();
                for (CapesListResponse.CapeInfo cape : response.getCapes()) {
                    if (cape.isAvailable()) {
                        newAvailableIds.add(cape.getId().toLowerCase());
                    }
                }

                // Atomically swap both fields together to prevent race conditions
                capeData.set(new CapeData(response.getCapes(), newAvailableIds));

                long availableCount = response.getCapes().stream().filter(CapesListResponse.CapeInfo::isAvailable)
                        .count();
                getLogger().info("Fetched " + availableCount + "/" + response.getCapes().size() +
                        " available capes from API");
            } catch (CapesApiClient.CapesApiException e) {
                getLogger().warning("Failed to fetch available capes: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (audiences != null) {
            audiences.close();
        }
        instance = null;
        getLogger().info("Custom Capes disabled.");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reload() {
        config = Config.load(getDataFolder());
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());
        refreshAvailableCapes();
        getLogger().info("Configuration reloaded.");
    }

    @NotNull
    public static CustomCapesPlugin get() {
        return instance;
    }

    @NotNull
    public Config getPluginConfig() {
        return config;
    }

    @NotNull
    public CapesApiClient getApiClient() {
        return apiClient;
    }

    @NotNull
    public BukkitAudiences getAudiences() {
        return audiences;
    }

    @NotNull
    public SkinApplierBukkit getSkinApplier() {
        return skinApplier;
    }

    /**
     * Get the cached list of available capes from the API.
     * May be null if not yet fetched.
     */
    @Nullable
    public List<CapesListResponse.CapeInfo> getAvailableCapes() {
        CapeData data = capeData.get();
        return data != null ? data.capes : null;
    }

    /**
     * Check if a cape type ID is available.
     * If capes haven't been loaded yet, returns true to allow the request
     * (the API will validate availability server-side).
     */
    public boolean isCapeAvailable(@NotNull String capeId) {
        CapeData data = capeData.get();

        // If capes haven't been loaded yet, allow the request - API will validate
        if (data == null) {
            return true;
        }

        // Check against the loaded available IDs
        return data.availableIds.contains(capeId.toLowerCase());
    }
}
