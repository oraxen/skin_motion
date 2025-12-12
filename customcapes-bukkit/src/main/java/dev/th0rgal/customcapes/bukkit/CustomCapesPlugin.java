package dev.th0rgal.customcapes.bukkit;

import dev.th0rgal.customcapes.bukkit.commands.CapeCommand;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
import dev.th0rgal.customcapes.core.config.Config;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main entry point for the Custom Capes Bukkit/Paper plugin.
 */
public final class CustomCapesPlugin extends JavaPlugin {

    private static CustomCapesPlugin instance;

    private Config config;
    private CapesApiClient apiClient;
    private BukkitAudiences audiences;
    private SkinApplierBukkit skinApplier;

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

        // Check API health in background
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (apiClient.isHealthy()) {
                getLogger().info("Capes API is reachable and healthy.");
            } else {
                getLogger().warning("Capes API is not reachable at " + config.getApiUrl());
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
}

